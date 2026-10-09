# Документы — приём и разбор документов соискателей

Хакатон «СберБизнесВайб», кейс 9.1.2. Два интерфейса: **кабинет соискателя** (`/apply`) и **рабочее место оператора** (`/operator`). Соискатель отвечает на пару вопросов — сервер подбирает нужный набор документов; файлы принимаются мгновенно, а разбор идёт в фоне через видимые стадии: текст (PDF-слой или OCR) → тип документа по содержимому → поля по схеме типа → проверки (контрольные суммы, MRZ, даты) → готово или «нужна проверка». Оператор видит очередь, оригинал рядом с полями, правит и подтверждает. Архитектура повторяет «Проходную»: микросервисы на Java/Spring за gateway, Python там, где он сильнее (OCR), события через Kafka + outbox, вход персонала через Keycloak, модель за изолированным `LlmGateway` с локальным провайдером Ollama.

## Быстрый старт (≤ 10 минут)
```bash
git clone <repo> && cd <repo>/documents
scripts/gen-env.sh               # infra/.env со случайными секретами; локальная модель: LLM_PROVIDER=ollama
cd infra && docker compose --profile llm-local up -d --build
```
- Соискатель: `https://<host>/apply` → анкета → загрузка файлов, ссылка-ключ для возврата к заявке
- Оператор: `https://<host>/operator` → вход через Keycloak (демо-оператор `operator` / `KC_DEMO_OPERATOR_PASSWORD`)
- Keycloak: `https://auth.<host>`
- Health: `https://<host>/actuator/health`, сервисы — внутри сети, `scripts/smoke.sh https://<host>`

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
| [docs/EVAL.md](docs/EVAL.md) | как честно меряем качество разбора, без подгонки |
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
