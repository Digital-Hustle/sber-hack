# AGENTS.md — сервис people (Java 21, Spring Boot)

Этот файл важнее `proxodnaya/AGENTS.md` для всего внутри `services/people/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B1. **Порт:** 8081. **БД:** `people`. Источник правды о людях, устройствах, зонах и проходных.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом сервисе.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, handoff, конфликты).

## Команды
```bash
# из services/people/
(cd .. && ./gradlew :people:check)
(cd .. && ./gradlew :people:update :people:generateJooq)   # Liquibase → jOOQ (нужен Postgres)
(cd ../.. && scripts/new-migration.sh people <slug>)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
