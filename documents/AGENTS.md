# AGENTS.md — кейс «Документы» (папка `documents/`)

Проект: **«Документы»** — приём и разбор документов соискателей. Хакатон «СберБизнесВайб», кейс 9.1.2.
Судьи проверяют вживую на нашем стенде. Есть условия допуска (Д1–Д12, зачёт/незачёт) и снятия с конкурса. Самый дорогой блок — **качество разбора на закрытом наборе (30 баллов)**: всё, что повышает честную точность извлечения, — приоритет.

## Прочитай перед любой задачей
0. **Скилл `../.agents/skills/documents-dev/SKILL.md`** — цикл задача → PR и скрипты. Для кода: бэкенд — `backend-spring`, фронт — `frontend-fsd`.
1. `rules.md` — жёсткие правила. Нарушение = риск снятия всей команды.
2. `docs/ARCHITECTURE.md` — сервисы, конвейер разбора, стадии, модель данных.
3. `docs/REQUIREMENTS.md` — FR/NFR с ID. В каждом коммите ссылайся на ID требования.
4. `docs/DOC-TYPES.md` — виды документов, поля и проверки (источник правды для схем извлечения).
5. `docs/API.md` — контракт, стадии и коды. Сначала контракт, потом код.
6. `docs/DECISIONS.md` — журнал решений. Новое архитектурное решение = новая запись ADR.
7. **`AGENTS.md` модуля** (`services/<svc>/`, `frontend/`) — он важнее этого файла; там же `docs/{PRD,ARCHITECTURE,RULES,TASKS,MEMORY}.md`. Начинай с `MEMORY.md` и своей строки в `TASKS.md`.
8. `docs/WORKFLOW.md` — параллельная работа: draft PR, handoff, конфликты, локальный CI.

## Карта папки
```
services/     Gradle Kotlin DSL (один wrapper, version catalog, build-logic), Java 21, Spring Boot 3.5
  build-logic/ gradle/ config/checkstyle/   создаются на Т0: init-backend.sh services (кейс = documents)
  libs/common/  ProblemDetail, CurrentUser, outbox, idempotent consumer, конверт событий
  libs/llm/     LlmGateway + адаптеры gigachat/openai/ollama — единственное место с провайдерами (Д6)
  gateway/ intake/ docproc/ review/   Java-сервисы (у каждого AGENTS.md + docs/)
  ocr/          Python 3.12 + FastAPI: текстовый слой PDF, OCR, качество снимка, MRZ (uv)
contracts/    openapi/<service>.json, events/<topic>/<Type>.schema.json
frontend/     одно Vite-приложение FSD, зоны /apply и /operator; AGENTS.md + docs/ (DESIGN.md)
infra/        docker-compose.yml, Caddyfile, keycloak/realm-documents.json, ollama/, .env.example
eval/         прогон качества на открытом наборе (сам набор — вне git), см. docs/EVAL.md
docs/         документация (допуск Д10)
scripts/      task-start, ci-local, handoff, open-pr, verify, guard, new-migration, gen-env, smoke, install-hooks
```

## Владельцы модулей
| Модуль | Владелец |
|---|---|
| gateway, intake, libs/common, build-logic, infra, CI | B1 |
| docproc, ocr (Python), промпты и схемы извлечения, eval | B2 |
| review, libs/llm (адаптеры, локальный провайдер) | B3 |
| frontend: зона apply (её pages/widgets/features) | F1 |
| frontend: зона operator, app, shared, entities | F2 |

## Как работать агенту
- **Постоянная цель — draft PR твоей задачи с зелёными проверками и честным handoff.** Одна задача (строка TASKS.md) = одна ветка = один worktree = один PR.
- Старт: `scripts/task-start.sh <TASK> <FR> <slug>`. Продолжаешь чужую сессию — сначала Handoff в PR и `MEMORY.md` модуля.
- По ходу: тест → код → `scripts/ci-local.sh quick` → коммит → push (каждые 20–40 минут).
- Финиш: `scripts/ci-local.sh full`, статус `review` в TASKS.md, `scripts/open-pr.sh <ID> "<заголовок>"`. Мерж — по `docs/MEMORY.md §3` (делегирование).
- Меняешь контракт — обнови `docs/API.md`, `./gradlew :<svc>:exportOpenApi`, на фронте `npm run gen:api`.
- Меняешь схему поля или вид документа — сначала `docs/DOC-TYPES.md`, затем миграция реестра `doc_types`, затем прогон `eval/` (качество не должно упасть).
- Не придумывай данные, моки и «временные» заглушки в боевом пути (rules.md §1). Не подгоняй под файлы открытого набора.
- В конце ответа человеку: что сделано, файлы, как проверить руками, что осталось.
