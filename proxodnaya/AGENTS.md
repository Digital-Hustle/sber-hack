# AGENTS.md — кейс «Проходная» (папка `proxodnaya/`)

Проект: **«Проходная»** — контроль и учёт людей на стройплощадке. Хакатон «СберБизнесВайб», кейс 1.
Судьи проверяют всё вживую, руками, на нашем стенде. Есть условия допуска (зачёт/незачёт) и условия снятия с конкурса.

## Прочитай перед любой задачей
0. **Скилл `../.agents/skills/proxodnaya-dev/SKILL.md`** (в корне репозитория) — цикл задача → PR и скрипты.
1. `rules.md` — жёсткие правила. Нарушение = риск снятия всей команды.
2. `docs/ARCHITECTURE.md` — модули, границы, модель данных, поток прохода.
3. `docs/REQUIREMENTS.md` — FR/NFR с ID. В каждом коммите ссылайся на ID требования.
4. `docs/API.md` — контракт и коды отказов. Сначала контракт, потом код.
5. `docs/DECISIONS.md` — журнал решений. Новое архитектурное решение = новая запись ADR.

## Карта репозитория
```
services/     Maven multi-module, Java 21, Spring Boot 3
  libs/common/   security, ошибки, outbox, crypto, конверт событий
  gateway/ people/ access/ face/ shift/ assistant/ notify/
contracts/    openapi/<service>.json, events/*.schema.json
frontend/     pnpm workspace: apps/kiosk, apps/worker (PWA), apps/admin, packages/api, packages/ui
infra/        docker-compose.yml, Caddyfile, keycloak/realm-gate.json, ollama/, .env.example
docs/         вся документация (требование допуска Д8)
scripts/      verify.sh, guard.sh, open-pr.sh, new-migration.sh, smoke.sh, install-hooks.sh
```

## Владельцы модулей (спрашивай владельца, не трогай чужое без нужды)
| Модуль | Владелец |
|---|---|
| gateway, people, libs/common, infra, CI | B1 |
| access, face | B2 |
| shift, assistant, notify | B3 |
| frontend/apps/kiosk, frontend/apps/worker, packages/auth | F1 |
| frontend/apps/admin, packages/ui, packages/api | F2 |

## Как работать агенту
- Одна задача = одно требование (FR-xx) = маленький коммит. Не делай «заодно».
- Перед кодом: перечитай FR, проверь API.md, найди существующий код модуля.
- После кода: `scripts/verify.sh && scripts/guard.sh`, затем `scripts/open-pr.sh <ID> "<заголовок>"`.
- Если меняешь контракт — обнови `docs/API.md` и перегенерируй `packages/api` (`pnpm gen:api`).
- Не придумывай данные, моки и «временные» заглушки в боевом пути. См. rules.md §1.
- В конце ответа напиши человеку: что сделано, какие файлы, как проверить руками, что осталось.
