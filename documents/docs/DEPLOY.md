# Деплой и CI/CD — «Документы»

Схема та же, что у «Проходной» (`../../proxodnaya/docs/DEPLOY.md`): GHCR-образы, SSH-деплой, health-check и откат. Ниже — отличия.

## Сервер
- **8 vCPU / 32 ГБ RAM / 100 ГБ NVMe**, Ubuntu 24.04 (локальная модель с vision — самое тяжёлое). Без локальной модели (облачный провайдер) хватит 8 vCPU / 16 ГБ. С NVIDIA GPU разбор на локальной модели в 5–10 раз быстрее.
- Домен или `<ip>.sslip.io`; `auth.` — Keycloak. HTTPS обязателен (камера телефона).
- **Запасной стенд** — ноутбук 32 ГБ с тем же compose и скачанными моделями.

## Контейнеры (лимиты RAM)
| Сервис | RAM |
|---|---|
| caddy, frontend (nginx) | 100 МБ |
| gateway | 384 МБ |
| intake, review | по 512 МБ |
| docproc (воркеры, JSON Schema) | 768 МБ |
| ocr (Tesseract, OpenCV, рендер PDF; 2 процесса uvicorn) | 1 ГБ |
| keycloak | 1 ГБ |
| postgres (БД на сервис + keycloak, файлы в bytea) | 1 ГБ |
| kafka (KRaft, 1 брокер) | 1 ГБ |
| jaeger | 256 МБ |
| prometheus (профиль `observability`, хранение 7 дней) | 256 МБ |
| ollama: `qwen3:8b` + `qwen2.5vl:7b` (профиль `llm-local`) | 12 ГБ |
| **Итого** | **≈ 19–20 ГБ** |

## Подготовка сервера (B1, один раз)
1. Пользователь `deploy`, SSH-ключ, `ufw allow 22,80,443`; Docker + compose plugin.
2. `git clone` в `/opt/sber-hack` (нужна `documents/infra/`), `documents/scripts/gen-env.sh` на сервере, вписать хост и ключи моделей.
3. `docker login ghcr.io` (read-only токен); `docker compose --profile llm-local --profile observability up -d`; Prometheus — `ssh -L 9090:localhost:9090 deploy@<host>` → http://localhost:9090/alerts; `scripts/smoke.sh https://<host>`.

## CI (`.github/workflows/documents-ci.yml`)
На PR и push в `main` с изменениями `documents/**`: changes → services (матрица изменённых: Java — Postgres service, Liquibase, jOOQ, `check`, be-check, контракт; Python ocr — ruff, mypy, pytest) → frontend (`gen:api`, токены, lint, typecheck, build, fsd-check) → eval (синтетика, если есть `eval/run.py`) → security (gitleaks по истории, `documents/scripts/guard.sh`, Trivy) → infra (`compose config`) → smoke (`main` и PR с изменениями инфраструктуры: `compose up --build --wait`, `scripts/smoke.sh`, при секрете `DOCS_LLM_API_KEY` — `eval/run.py` на синтетике).

## CD (`.github/workflows/documents-deploy.yml`)
push в `main` → CI зелёный → `docker compose build` (те же Dockerfile, что локально: сборка внутри образа, ADR-014) → push `ghcr.io/<org>/documents/<svc>:<sha>` (gateway, intake, ocr, docproc, review, caddy с фронтом) → SSH: `git checkout <sha>`, в `.env` выставляются `IMAGE_REGISTRY`/`IMAGE_TAG` → `docker compose pull && up -d --no-build` → `scripts/smoke.sh` до 3 минут → при провале откат на прошлый тег. Ручной запуск — `workflow_dispatch` с тегом (`docs-demo-2`).
Включение: переменная репозитория `DOCS_DEPLOY_ENABLED=true` + секреты `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`. Секретов приложения в GitHub нет (кроме необязательного `DOCS_LLM_API_KEY` для eval в smoke CI).
Пока сервера нет, CD выключен; стенд поднимается одной командой `docker compose up -d --build` из чистого клона.

## Общий сервер (на нём живёт один кейс из трёх)
Перед деплоем — `docker compose ls`: чужой стенд остановить без `-v`.
Порты 80/443 держит общий edge-прокси (`../../edge/README.md`): TLS и маршрут по хосту. «Документы» за ним — в `infra/.env`:
`PUBLIC_HOST=documents.<домен>`, `OIDC_ISSUER_URI=https://documents.<домен>/auth/realms/documents`,
`PUBLIC_URL=https://documents.<домен>`, `CADDY_SCHEME=http://`, `HTTP_BIND=127.0.0.1:8180`, `HTTPS_BIND=127.0.0.1:8543`.
Без этих переменных стенд, как раньше, сам слушает 80/443.

## Перед защитой
Тег `docs-demo-final`, деплой тега, заморозка деплоя; маршрут судьи на сервере и на запасном ноутбуке; модели и образы — локально (`docker save` в архив).
