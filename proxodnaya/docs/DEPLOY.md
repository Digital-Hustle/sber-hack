# Деплой и CI/CD

## Сервер
- Рекомендуемый для микросервисов: **8 vCPU / 32 ГБ RAM / 100 ГБ NVMe** (минимум 8 vCPU / 16 ГБ), Ubuntu 24.04, без GPU. Почасовая оплата: Cloud.ru (облако Сбера), Selectel, Timeweb Cloud, Yandex Cloud.
- Минимальный: 4 vCPU / 8 ГБ, если модель работает через GigaChat или на ноутбуке.
- Домен: свой или `<ip>.sslip.io`; один хост: Keycloak под `/auth`, API через gateway, остальное — SPA (ADR-039). Caddy сам выпускает сертификаты Let's Encrypt.
- **Запасной стенд**: ноутбук с тем же compose и локальной моделью — на случай отказа интернета (Д1).

## Контейнеры
| Сервис | RAM (лимит) |
|---|---|
| caddy, frontend (nginx) | 100 МБ |
| gateway | 384 МБ |
| people, access, shift, assistant | по 384–512 МБ |
| face (Python, onnxruntime, модели) | 768 МБ |
| keycloak | 1 ГБ |
| postgres (БД на сервис + keycloak) | 768 МБ |
| kafka (KRaft, 1 брокер, `KAFKA_HEAP_OPTS=-Xmx512m`) | 1 ГБ |
| jaeger all-in-one | 256 МБ |
| ollama + qwen3:4b (профиль `llm-local`) | 4 ГБ |
| **Итого** | **≈ 11–12 ГБ** |

JVM: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k` и `mem_limit` в compose. Сервер **8 vCPU / 16 ГБ** — впритык, комфортно **8 vCPU / 32 ГБ**. Без локальной модели на сервере хватит 16 ГБ.
Запасной ноутбук: от 16 ГБ RAM (лучше 32).

## Подготовка сервера (один раз, B1)
1. Создать пользователя `deploy`, вход только по SSH-ключу, `ufw allow 22,80,443`.
2. Установить Docker + compose plugin.
3. `git clone` в `/opt/gate` (нужна только `proxodnaya/infra/`; compose берёт образы `ghcr.io/<org>/gate-<svc>:${IMAGE_TAG}`), создать `proxodnaya/infra/.env` скриптом `scripts/gen-env.sh` на сервере (+ вписать хост и ключ GigaChat). В CI секретов приложения нет.
4. `docker login ghcr.io` с read-only токеном.
5. `docker compose --profile llm-local up -d`, проверить `scripts/smoke.sh https://<host>`.

## CI (GitHub Actions, `.github/workflows/proxodnaya-ci.yml`)
Запускается на каждый PR и push в `main`:
1. **changes** — какие части затронуты (backend / frontend / infra / docs).
2. **services** — матрица только по изменённым сервисам (правка `libs/`, `build-logic/`, `gradle/` → все). Java: Postgres service container → `:svc:update :svc:generateJooq :svc:check` (checkstyle, unit + integration на Testcontainers, JaCoCo) → `be-check.mjs` → `exportOpenApi` + `git diff contracts/`; отчёт JUnit в PR. Python (face): `uv sync --frozen`, ruff, mypy, pytest.
3. **frontend** — `npm ci && npm run gen:api && npm run lint && npm run typecheck && npm run build && fsd-check`.
5. **security** — gitleaks (docker-образ, вся история) + Trivy fs (HIGH/CRITICAL = fail).
6. **smoke** (только `main`) — `docker compose up` в раннере + `scripts/smoke.sh`.

Для публичного репозитория минуты GitHub Actions бесплатные. Сервер CI не нагружает.

## CD (`.github/workflows/proxodnaya-deploy.yml`)
push в `main` → CI зелёный → jar-ы Gradle (с Postgres для jOOQ) → образы через общий `services/Dockerfile` (`--build-arg SERVICE=…`), face и `frontend` — своими Dockerfile, тег `sha` → push в GHCR → SSH на сервер: `docker compose pull && docker compose up -d` → health-check → при провале откат на предыдущий тег.
Секреты GitHub: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`. Секретов приложения в GitHub нет — они только в `.env` на сервере.

## Включение деплоя
Деплой выключен, пока в репозитории нет кода и сервера: в Settings → Variables задайте `DEPLOY_ENABLED=true` и секреты `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`.

## Branch protection для `main`
- Merge только через PR, обязательны проверки `backend`, `frontend`, `contract`, `security`.
- 1 approve от владельца модуля (CODEOWNERS). Агент не может апрувить свой PR.
- Squash merge, линейная история, запрет force-push.
- Ветка должна быть обновлена от `main` перед мержем.

## Перед защитой
- Тег `demo-final`, деплой этого тега, **заморозка деплоя** (отключить workflow).
- Прогнать маршрут судьи на сервере и на запасном ноутбуке.
