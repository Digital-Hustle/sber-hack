# Документы — приём и разбор документов соискателей

Хакатон «СберБизнесВайб», кейс 9.1.2. Два интерфейса: **кабинет соискателя** (`/apply`) и **рабочее место оператора** (`/operator`). Соискатель отвечает на пару вопросов — сервер подбирает нужный набор документов; файлы принимаются мгновенно, а разбор идёт в фоне через видимые стадии: текст (PDF-слой или OCR) → тип документа по содержимому → поля по схеме типа → проверки (контрольные суммы, MRZ, даты) → готово или «нужна проверка». Оператор видит очередь, оригинал рядом с полями, правит и подтверждает. Архитектура повторяет «Проходную»: микросервисы на Java/Spring за gateway, Python там, где он сильнее (OCR), события через Kafka + outbox, вход персонала через Keycloak, модель за изолированным `LlmGateway` с локальным провайдером Ollama.

## Быстрый старт (≤ 10 минут)
```bash
git clone https://github.com/Digital-Hustle/sber-hack && cd sber-hack/documents
scripts/gen-env.sh               # infra/.env со случайными секретами; PUBLIC_HOST, локальная модель: LLM_PROVIDER=ollama
cd infra && docker compose --profile llm-local up -d --build
```
- Всё на одном домене за Caddy (`infra/caddy/Caddyfile`, образ — `frontend/Dockerfile`: статика фронта + reverse proxy).
- Соискатель: `https://<host>/apply` → согласие → анкета → загрузка файлов (или камера телефона); ссылка-ключ `#k=…` и код возобновления — чтобы вернуться к заявке.
- Оператор: `https://<host>/operator` → вход через Keycloak. Демо-пользователи (пароли — в `infra/.env`): `operator` (`KC_DEMO_OPERATOR_PASSWORD`), `senior` (`KC_DEMO_SENIOR_PASSWORD`), `auditor` (`KC_DEMO_AUDITOR_PASSWORD`). Сценарий — [docs/DEMO.md](docs/DEMO.md).
- Keycloak: `https://<host>/auth` (realm `documents`).
- Health: `https://<host>/actuator/health`, сервисы — внутри сети, `scripts/smoke.sh https://<host>`.

## Проверки локально
```bash
cd documents/frontend && npm ci && npm run gen:api && npm run gen:tokens -- --check && npm run lint && npm run typecheck && npm run build && npm run fsd-check
npm run build && npx vite preview --port 4173 & CHROME=$(which chromium) node scripts/smoke-ui.mjs   # скриншоты /tmp/e2e-*.png
cd ../services && ./gradlew check                 # JDK 21; интеграционным тестам нужен PostgreSQL 16 (postgres/postgres, базы intake, docproc, review)
cd ../eval && uv sync && uv run ruff check . && uv run pytest -q && uv run python run.py --set synthetic
cd ../.. && git add -A documents && sh documents/scripts/guard.sh
```
Качество разбора на стенде: `EVAL_OPERATOR_TOKEN=… uv run python run.py --set synthetic --api https://<host>` ([docs/EVAL.md](docs/EVAL.md)).

## Ограничения
- Сервис review держит SSE-подписки в памяти процесса — запускать **в одном экземпляре** (то же для потоков intake).
- Лимитер запросов шлюза — в памяти процесса: gateway тоже в одном экземпляре (для нескольких — общий счётчик в Redis).
- Многостраничный файл с несколькими документами делится автоматически (FR-07), но только по страницам: если два документа на одной странице, разбирается основной, остальное — замечание оператору.
- Вход оператора не переживает перезагрузку страницы (токены только в памяти); Keycloak пускает обратно по SSO без пароля.

## Документация
| Файл | О чём |
|---|---|
| [AGENTS.md](AGENTS.md) | вход для ИИ-агентов |
| [rules.md](rules.md) | правила разработки и красные линии |
| [docs/PRD.md](docs/PRD.md) | продукт: проблемы, цели, пользователи, MVP |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | FR/NFR с ID и трассировка к критериям положения |
| [docs/DOC-TYPES.md](docs/DOC-TYPES.md) | восемь видов документов: поля, проверки, признаки |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | схемы, сервисы, конвейер разбора, модель данных |
| [docs/API.md](docs/API.md) | контракт, стадии и коды отказов |
| [docs/LLM.md](docs/LLM.md) | слой модели и **инструкция по локальному провайдеру** |
| [docs/EVAL.md](docs/EVAL.md) · `eval/` | как честно меряем качество разбора, без подгонки; синтетика и `run.py` |
| [docs/DECISIONS.md](docs/DECISIONS.md) | журнал решений и лицензии |
| [docs/SECURITY.md](docs/SECURITY.md) | вход, ПДн, модель угроз, аудит |
| [docs/STACK.md](docs/STACK.md) · [docs/DEPLOY.md](docs/DEPLOY.md) | стек и лицензии, сервер и CI/CD |
| [docs/ROLES.md](docs/ROLES.md) · [docs/PLAN.md](docs/PLAN.md) · [docs/DEMO.md](docs/DEMO.md) | команда, план дня, защита |
| [docs/WORKFLOW.md](docs/WORKFLOW.md) · [docs/TASKS.md](docs/TASKS.md) · [docs/MEMORY.md](docs/MEMORY.md) | процесс, вехи, состояние |
| [docs/AGENT_SKILLS.md](docs/AGENT_SKILLS.md) · [docs/QUESTIONS.md](docs/QUESTIONS.md) | скиллы агентов, открытые вопросы |
| `services/<svc>/docs/`, [frontend/docs/](frontend/docs/) | PRD, ARCHITECTURE, RULES, TASKS, MEMORY модулей; `frontend/docs/DESIGN.md` — дизайн-система |

## Команда
| Участник | Зона ответственности |
|---|---|
| B1 | платформа и безопасность: gateway, intake (приём, проверки, дубликаты, набор документов), Keycloak, CI/CD |
| B2 | качество разбора: docproc (очередь, стадии, классификация, извлечение, проверки полей), ocr (Python), промпты, оценка |
| B3 | оператор и модель: review (очередь, сверка, правки, история, выгрузки, метрики), `libs/llm` и локальный провайдер |
| F1 | кабинет соискателя `/apply` |
| F2 | рабочее место оператора `/operator`, дизайн-система, `shared` |
