# Деплой и CI/CD

## Сервер
- Рекомендуемый для микросервисов: **8 vCPU / 32 ГБ RAM / 100 ГБ NVMe** (минимум 8 vCPU / 16 ГБ), Ubuntu 24.04, без GPU. Почасовая оплата: Cloud.ru (облако Сбера), Selectel, Timeweb Cloud, Yandex Cloud.
- Минимальный: 4 vCPU / 8 ГБ, если модель работает через GigaChat или на ноутбуке.
- Домен: свой или `<ip>.sslip.io`; поддомены `auth.` (Keycloak) и основной (всё остальное). Caddy сам выпускает сертификаты Let's Encrypt.
- **Запасной стенд**: ноутбук с тем же compose и локальной моделью — на случай отказа интернета (Д1).

## Контейнеры
| Сервис | RAM (лимит) |
|---|---|
| caddy, frontend (nginx) | 100 МБ |
| gateway | 384 МБ |
| people, access, shift, assistant, notify | по 384–512 МБ |
| face (OpenCV, модели) | 768 МБ |
| keycloak | 1 ГБ |
| postgres (БД на сервис + keycloak) | 768 МБ |
| rabbitmq | 256 МБ |
| jaeger all-in-one | 256 МБ |
| ollama + qwen3:4b (профиль `llm-local`) | 4 ГБ |
| **Итого** | **≈ 10–11 ГБ** |

JVM: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k` и `mem_limit` в compose. Сервер **8 vCPU / 16 ГБ** — впритык, комфортно **8 vCPU / 32 ГБ**. Без локальной модели на сервере хватит 16 ГБ.
Запасной ноутбук: от 16 ГБ RAM (лучше 32).

## Подготовка сервера (один раз, B1)
1. Создать пользователя `deploy`, вход только по SSH-ключу, `ufw allow 22,80,443`.
2. Установить Docker + compose plugin.
3. `git clone` в `/opt/gate` (нужна только `proxodnaya/infra/`; compose берёт образы `ghcr.io/<org>/gate-<svc>:${IMAGE_TAG}`), создать `proxodnaya/infra/.env` из `.env.example` руками на сервере. В CI секретов приложения нет.
4. `docker login ghcr.io` с read-only токеном.
5. `docker compose --profile llm-local up -d`, проверить `https://<host>/api/actuator/health`.

## CI (GitHub Actions, `.github/workflows/proxodnaya-ci.yml`)
Запускается на каждый PR и push в `main`:
1. **changes** — какие части затронуты (backend / frontend / infra / docs).
2. **services** — матрица по сервисам: собирается только изменённый сервис и `libs/common` (`./mvnw -B -pl <svc> -am verify`).
3. **frontend** — `pnpm install --frozen-lockfile && pnpm -r lint && pnpm -r typecheck && pnpm -r build`.
4. **contract** — `contracts/openapi/<svc>.json` совпадают со сгенерированными; схемы событий валидны.
5. **security** — gitleaks (docker-образ, вся история) + Trivy fs (HIGH/CRITICAL = fail).
6. **smoke** (только `main`) — `docker compose up` в раннере + `scripts/smoke.sh`.

Для публичного репозитория минуты GitHub Actions бесплатные. Сервер CI не нагружает.

## CD (`.github/workflows/proxodnaya-deploy.yml`)
push в `main` → CI зелёный → сборка образов всех сервисов и `frontend` с тегом `sha` → push в GHCR → SSH на сервер: `docker compose pull && docker compose up -d` → health-check → при провале откат на предыдущий тег.
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
