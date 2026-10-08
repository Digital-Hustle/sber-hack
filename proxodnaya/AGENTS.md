# AGENTS.md — кейс «Проходная» (папка `proxodnaya/`)

Проект: **«Проходная»** — контроль и учёт людей на стройплощадке. Хакатон «СберБизнесВайб», кейс 1.
Судьи проверяют всё вживую, руками, на нашем стенде. Есть условия допуска (зачёт/незачёт) и условия снятия с конкурса.

## Прочитай перед любой задачей
0. **Скилл `../.agents/skills/proxodnaya-dev/SKILL.md`** (в корне репозитория) — цикл задача → PR и скрипты. Для кода: бэкенд — `backend-spring`, фронт — `frontend-fsd`.
1. `rules.md` — жёсткие правила. Нарушение = риск снятия всей команды.
2. `docs/ARCHITECTURE.md` — модули, границы, модель данных, поток прохода.
3. `docs/REQUIREMENTS.md` — FR/NFR с ID. В каждом коммите ссылайся на ID требования.
4. `docs/API.md` — контракт и коды отказов. Сначала контракт, потом код.
5. `docs/DECISIONS.md` — журнал решений. Новое архитектурное решение = новая запись ADR.

## Карта репозитория
```
services/     Gradle Kotlin DSL (один wrapper, version catalog, build-logic), Java 21, Spring Boot 3.5
  build-logic/   convention plugins: proxodnaya.java / .service / .jooq / .gateway / .library
  gradle/libs.versions.toml   все версии
  config/checkstyle/          checkstyle из sueta-rnd-back
  libs/common/   security, ProblemDetail, CurrentUser, outbox, idempotent consumer, конверт событий
  gateway/ people/ access/ shift/ assistant/   Java-сервисы
  face/          Python 3.12 + FastAPI + onnxruntime (uv)
contracts/    openapi/<service>.json, events/<topic>/<Type>.schema.json
frontend/     одно Vite-приложение FSD: src/{app,pages,widgets,features,entities,shared}, зоны /kiosk /worker /admin
infra/        docker-compose.yml, Caddyfile, keycloak/realm-proxodnaya.json, ollama/, .env.example
docs/         вся документация (требование допуска Д8)
scripts/      verify.sh, guard.sh, open-pr.sh, new-migration.sh, gen-env.sh, smoke.sh, install-hooks.sh
```

## Владельцы модулей (спрашивай владельца, не трогай чужое без нужды)
| Модуль | Владелец |
|---|---|
| gateway, people, libs/common, build-logic, infra, CI | B1 |
| access, face (Python) | B2 |
| shift (смены, отчёты, оповещения/SSE), assistant | B3 |
| frontend: зоны kiosk и worker (их pages/widgets/features) | F1 |
| frontend: зона admin, app, shared, entities | F2 |

## Как работать агенту
- Одна задача = одно требование (FR-xx) = маленький коммит. Не делай «заодно».
- Перед кодом: перечитай FR, проверь API.md, найди существующий код модуля.
- После кода: `scripts/verify.sh && scripts/guard.sh`, затем `scripts/open-pr.sh <ID> "<заголовок>"`.
- Если меняешь контракт — обнови `docs/API.md`, `./gradlew :<svc>:exportOpenApi` и на фронте `npm run gen:api`.
- Не придумывай данные, моки и «временные» заглушки в боевом пути. См. rules.md §1.
- В конце ответа напиши человеку: что сделано, какие файлы, как проверить руками, что осталось.
