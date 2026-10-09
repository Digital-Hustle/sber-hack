# Деплой и CI/CD — «Субсидии»

Схема та же, что у «Проходной» (`../../proxodnaya/docs/DEPLOY.md`): GHCR-образы, SSH-деплой, health-check и откат. Ниже — отличия.

## Сервер
- **8 vCPU / 32 ГБ RAM / 100 ГБ NVMe**, Ubuntu 24.04 (локальная модель с vision — самое тяжёлое). Без локальной модели (облачный провайдер) хватит 8 vCPU / 16 ГБ. С NVIDIA GPU разбор на локальной модели в 5–10 раз быстрее.
- Домен или `<ip>.sslip.io`; Keycloak — под `https://<host>/auth` (Caddy, `KC_HTTP_RELATIVE_PATH=/auth`). HTTPS обязателен; без домена — `tls internal` (внутренний CA Caddy).
- Текущий стенд команды: `217.12.40.215` (Debian 13, 10 CPU, 20 ГБ RAM, 40 ГБ) — общий с кейсом «Документы»: следить за RAM, локальные модели (~11 ГБ) и стенд «Документов» вместе впритык.
- **Запасной стенд** — ноутбук 32 ГБ с тем же compose и скачанными моделями.

## Контейнеры (лимиты RAM)
| Сервис | RAM |
|---|---|
| caddy, frontend (nginx) | 100 МБ |
| gateway | 384 МБ |
| package, rules | по 512 МБ |
| docproc (воркеры, JSON Schema) | 768 МБ |
| ocr (Tesseract, OpenCV, рендер PDF; 2 процесса uvicorn) | 1 ГБ |
| keycloak | 1 ГБ |
| postgres (БД на сервис + keycloak, файлы в bytea) | 1 ГБ |
| kafka (KRaft, 1 брокер) | 1 ГБ |
| jaeger | 256 МБ |
| ollama: `qwen3:8b` + `qwen2.5vl:7b` (профиль `llm-local`) | 12 ГБ |
| **Итого** | **≈ 19–20 ГБ** (с моделью по API — ≈ 7–8 ГБ, ollama не нужен) |

## Подготовка сервера (B1, один раз)
1. Пользователь `deploy`, SSH-ключ, `ufw allow 22,80,443`; Docker + compose plugin.
2. `git clone` в `/opt/sber-hack` (нужна `subsidy/infra/`), `subsidy/scripts/gen-env.sh` на сервере, вписать хост и ключи моделей.
3. `docker login ghcr.io` (read-only токен); `scripts/smoke.sh https://<host>` после запуска:
   - модель локально: `LLM_PROVIDER=ollama`, `docker compose --profile llm-local up -d`;
   - модель по API (OpenAI-совместимый шлюз): `LLM_PROVIDER=openai` + `LLM_BASE_URL`/`LLM_MODEL`/`LLM_VISION_MODEL`/`LLM_API_KEY`/`LLM_JSON_MODE` в `infra/.env` (пример — `docs/LLM.md`), `docker compose up -d` без профиля.
   Переключение — правка `.env` и `docker compose up -d docproc assistant`, пересборка не нужна.

## CI на сервере (основной, ADR-017)
GitHub Actions сейчас не стартуют (биллинг аккаунта), поэтому те же шаги гоняет `/root/sub-ci/run.sh pr-N` на сервере (под `flock`, своя БД `sub-pg` на 5434) и ставит commit status `server-ci`. Мерж — при зелёном `server-ci`. Подробно — в handoff агентов и `docs/WORKFLOW.md`.

## CI (`.github/workflows/subsidy-ci.yml`, когда Actions снова доступны)
На PR и push в `main` с изменениями `subsidy/**`: changes → services (матрица изменённых: Java — Postgres service, Liquibase, jOOQ, `check`, be-check, контракт; Python ocr — ruff, mypy, pytest) → frontend (`gen:api`, токены, lint, typecheck, build, fsd-check) → eval (синтетика, если есть `eval/run.py`) → security (gitleaks по истории, `subsidy/scripts/guard.sh`, Trivy) → infra (`compose config`) → smoke (только `main`).

## CD (`.github/workflows/subsidy-deploy.yml`)
push в `main` → CI зелёный → jar-ы → образы `ghcr.io/<org>/subs-<svc>:<sha>` → SSH: `docker compose pull && up -d` → health-check → откат на прошлый тег. Включение: переменная репозитория `SUBS_DEPLOY_ENABLED=true` + секреты `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`. Секретов приложения в GitHub нет.

## Перед защитой
Тег `subs-demo-final`, деплой тега, заморозка деплоя; маршрут судьи на сервере и на запасном ноутбуке; модели и образы — локально (`docker save` в архив).
