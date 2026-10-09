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

## Стенд за edge (общий сервер 217.12.40.215)
Порты 80/443 держит общий edge сервера (`/opt/sber-hack/edge`, не трогать): он маршрутизирует `proxodnaya.digital-hustle.ru` → `127.0.0.1:8380`. Caddy «Проходной» слушает только локально и работает по http, TLS — на edge.
- Checkout: `/opt/gate` (отдельно от `/opt/sber-hack`), `proxodnaya/infra/.env` — из `scripts/gen-env.sh`, затем правки:
  `PUBLIC_HOST=proxodnaya.digital-hustle.ru`, `CADDY_SCHEME=http://`, `HTTP_BIND=127.0.0.1:8380`, `HTTPS_BIND=127.0.0.1:8743`;
  модель по API: `LLM_PROVIDER=openai`, `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`, `LLM_TIMEOUT_SECONDS=60` (профиль `llm-local` не включаем — сервер общий);
  `SIMULATOR_ENABLED=false`, `STAFF_BOOTSTRAP_ADMIN_EMAIL=<почта первого админа>`, `SMTP_*`; пока SMTP нет — `STAFF_LOG_CODES=true` (код входа в `docker logs proxodnaya-people-1`).
- Caddy доверяет `X-Forwarded-*` от edge (`trusted_proxies static private_ranges`), поэтому Keycloak и ссылки видят https.
- Деплой без GHCR: `scripts/deploy-server.sh` — jar-ы (jOOQ из временного postgres `gate-build-pg` на `127.0.0.1:5439`, чтобы не мешать серверному CI на 5432), `docker compose -p proxodnaya build`, `up -d --wait`, `docker image prune -f`.
  `ssh srv 'cd /opt/gate && git fetch -q origin && git checkout -q -f origin/main && (setsid nohup proxodnaya/scripts/deploy-server.sh > /opt/gate/deploy.log 2>&1 < /dev/null &)'`
- Проверка: `scripts/smoke.sh https://proxodnaya.digital-hustle.ru`. Не запускать деплой одновременно с серверным CI.

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
