# Деплой и CI/CD

## Сервер
- Рекомендуемый: **8 vCPU / 16 ГБ RAM / 80–100 ГБ NVMe**, Ubuntu 24.04, без GPU. Почасовая оплата: Cloud.ru (облако Сбера), Selectel, Timeweb Cloud, Yandex Cloud.
- Минимальный: 4 vCPU / 8 ГБ, если модель работает через GigaChat или на ноутбуке.
- Домен: свой или `<ip>.sslip.io`; поддомены `auth.` (Keycloak) и основной (всё остальное). Caddy сам выпускает сертификаты Let's Encrypt.
- **Запасной стенд**: ноутбук с тем же compose и локальной моделью — на случай отказа интернета (Д1).

## Контейнеры
| Сервис | Образ | RAM |
|---|---|---|
| caddy | caddy:2 | 50 МБ |
| frontend | nginx со статикой трёх приложений | 50 МБ |
| backend | ghcr.io/<org>/gate-backend | 1–1,5 ГБ |
| keycloak | quay.io/keycloak/keycloak:26 (`start --import-realm`, БД в Postgres) | 0,7–1 ГБ |
| postgres | postgres:16 (БД `gate` и `keycloak`, именованный volume) | 0,5 ГБ |
| ollama | ollama/ollama (профиль `llm-local`) | 3–4 ГБ |
| prometheus | prom/prometheus (опционально) | 0,3 ГБ |

## Подготовка сервера (один раз, B1)
1. Создать пользователя `deploy`, вход только по SSH-ключу, `ufw allow 22,80,443`.
2. Установить Docker + compose plugin.
3. `git clone` в `/opt/gate` (нужна только `proxodnaya/infra/`), создать `proxodnaya/infra/.env` из `.env.example` руками на сервере. В CI секретов приложения нет.
4. `docker login ghcr.io` с read-only токеном.
5. `docker compose --profile llm-local up -d`, проверить `https://<host>/api/actuator/health`.

## CI (GitHub Actions, `.github/workflows/proxodnaya-ci.yml`)
Запускается на каждый PR и push в `main`:
1. **changes** — какие части затронуты (backend / frontend / infra / docs).
2. **backend** — `./mvnw -B verify` (unit + ArchUnit + Testcontainers).
3. **frontend** — `pnpm install --frozen-lockfile && pnpm -r lint && pnpm -r typecheck && pnpm -r build`.
4. **contract** — `openapi.json` в репо совпадает с тем, что генерирует backend.
5. **security** — gitleaks (docker-образ, вся история) + Trivy fs (HIGH/CRITICAL = fail).
6. **smoke** (только `main`) — `docker compose up` в раннере + `scripts/smoke.sh`.

Для публичного репозитория минуты GitHub Actions бесплатные. Сервер CI не нагружает.

## CD (`.github/workflows/proxodnaya-deploy.yml`)
push в `main` → CI зелёный → сборка образов `backend` и `frontend` с тегом `sha` → push в GHCR → SSH на сервер: `docker compose pull && docker compose up -d` → health-check → при провале откат на предыдущий тег.
Секреты GitHub: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`. Секретов приложения в GitHub нет — они только в `.env` на сервере.

## Branch protection для `main`
- Merge только через PR, обязательны проверки `backend`, `frontend`, `contract`, `security`.
- 1 approve от владельца модуля (CODEOWNERS). Агент не может апрувить свой PR.
- Squash merge, линейная история, запрет force-push.
- Ветка должна быть обновлена от `main` перед мержем.

## Перед защитой
- Тег `demo-final`, деплой этого тега, **заморозка деплоя** (отключить workflow).
- Прогнать маршрут судьи на сервере и на запасном ноутбуке.
