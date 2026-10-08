# Проходная — контроль и учёт людей на стройплощадке

Хакатон «СберБизнесВайб», кейс 1. Три интерфейса: **киоск проходной**, **телефон рабочего (PWA)**, **админка**. Решение о проходе принимает access: одноразовый QR, который телефон подписывает сам **без интернета**, + сверка лица 1:1 (face на Python) + живость + правила смены и допуска. Архитектура — микросервисы на Java/Spring за gateway, события через Kafka, вход персонала через Keycloak SSO.

## Быстрый старт (≤ 10 минут)
```bash
git clone <repo> && cd <repo>/infra
cp .env.example .env            # заполнить секреты; для локальной модели LLM_PROVIDER=ollama
docker compose --profile llm-local up -d --build
```
- Админка: `https://<host>/admin` → вход через Keycloak (демо-админ `admin` / `KC_DEMO_ADMIN_PASSWORD`, при первом входе — настройка TOTP)
- Keycloak: `https://auth.<host>`
- Киоск: `https://<host>/kiosk` → вход охранника через SSO → код сопряжения из админки
- Телефон: `https://<host>/worker` → инвайт-QR из админки
- Health: `https://<host>/api/gateway/health`, сервисы — `/api/<svc>/actuator/health`

## Документация
| Файл | О чём |
|---|---|
| [AGENTS.md](AGENTS.md) | вход для ИИ-агентов |
| [rules.md](rules.md) | правила разработки и красные линии |
| [docs/STACK.md](docs/STACK.md) | стек и лицензии |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | схемы, модули, модель данных, алгоритмы |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | FR/NFR и трассировка к критериям |
| [docs/API.md](docs/API.md) | контракт и коды отказов |
| [docs/LLM.md](docs/LLM.md) | слой модели и **инструкция по локальному провайдеру** |
| [docs/DECISIONS.md](docs/DECISIONS.md) | журнал решений |
| [docs/SECURITY.md](docs/SECURITY.md) | Keycloak/SSO, модель угроз, шифрование, аудит |
| [docs/DEPLOY.md](docs/DEPLOY.md) | сервер, CI/CD, откат |
| [docs/AGENT_SKILLS.md](docs/AGENT_SKILLS.md) | скиллы агентов: наш и сторонние |
| [docs/ROLES.md](docs/ROLES.md) · [docs/PLAN.md](docs/PLAN.md) · [docs/DEMO.md](docs/DEMO.md) | команда, план, защита |

## Команда
| Участник | Зона ответственности |
|---|---|
| B1 | платформа и безопасность: Keycloak/SSO, CI/CD, шифрование, аудит |
| B2 | проход и биометрия: движок решений, журнал, лицо, живость |
| B3 | смены, аналитика, realtime, оповещения, LLM-помощник |
| F1 | киоск, приложение рабочего |
| F2 | админка, дизайн-система |
