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
6. **`AGENTS.md` модуля, в котором работаешь** (`services/<svc>/`, `frontend/`) — он важнее этого файла; там же `docs/{PRD,ARCHITECTURE,RULES,TASKS,MEMORY}.md` (+ `DESIGN.md` у фронта). Начинай с `MEMORY.md` и своей строки в `TASKS.md`.
7. `docs/WORKFLOW.md` — как работаем параллельно: draft PR, handoff между сессиями, конфликты, локальный CI.

## Карта репозитория
```
services/     Gradle Kotlin DSL (один wrapper, version catalog, build-logic), Java 21, Spring Boot 3.5
  build-logic/   convention plugins: proxodnaya.java / .service / .jooq / .gateway / .library
  gradle/libs.versions.toml   все версии
  config/checkstyle/          checkstyle из sueta-rnd-back
  libs/common/   security, ProblemDetail, CurrentUser, outbox, idempotent consumer, конверт событий
  gateway/ people/ access/ shift/ assistant/   Java-сервисы (у каждого AGENTS.md + docs/)
  face/          Python 3.12 + FastAPI + onnxruntime (uv)
contracts/    openapi/<service>.json, events/<topic>/<Type>.schema.json
frontend/     одно Vite-приложение FSD: src/{app,pages,widgets,features,entities,shared}, зоны /kiosk /worker /admin; AGENTS.md + docs/ (DESIGN.md — дизайн-система)
infra/        docker-compose.yml, Caddyfile, keycloak/realm-proxodnaya.json, ollama/, .env.example
docs/         вся документация (требование допуска Д8): PRD, TASKS (вехи, платформа), MEMORY (состояние проекта), WORKFLOW
scripts/      task-start.sh, ci-local.sh, handoff.sh, open-pr.sh, verify.sh, guard.sh, new-migration.sh, gen-env.sh, smoke.sh, install-hooks.sh
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
- **Постоянная цель — draft PR твоей задачи с зелёными проверками и честным handoff.** Одна задача (строка TASKS.md) = одна ветка = один worktree = один PR. Не делай «заодно».
- Старт: `scripts/task-start.sh <TASK> <FR> <slug>` (worktree, статус doing, draft PR). Продолжаешь чужую сессию — сначала Handoff в PR и `MEMORY.md` модуля (`docs/WORKFLOW.md §3`).
- Перед кодом: перечитай FR, проверь API.md, найди существующий код модуля.
- По ходу: тест → код → `scripts/ci-local.sh quick` → коммит → push (каждые 20–40 минут).
- Уходишь/кончается контекст: `scripts/handoff.sh` → раздел Handoff в PR, §1 и журнал `MEMORY.md`.
- Финиш: `scripts/ci-local.sh full`, статус `review` в TASKS.md, затем `scripts/open-pr.sh <ID> "<заголовок>"` (draft → ready). Мержит человек.
- Если меняешь контракт — обнови `docs/API.md`, `./gradlew :<svc>:exportOpenApi` и на фронте `npm run gen:api`.
- Не придумывай данные, моки и «временные» заглушки в боевом пути. См. rules.md §1.
- В конце ответа напиши человеку: что сделано, какие файлы, как проверить руками, что осталось.
