# AGENTS.md — сервис shift (Java 21, Spring Boot)

Этот файл важнее `proxodnaya/AGENTS.md` для всего внутри `services/shift/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B3. **Порт:** 8083. **БД:** `shift`. Смены, интервалы присутствия, аналитика, табель, оповещения и SSE.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом сервисе.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, handoff, конфликты).

## Команды
```bash
# из services/shift/
(cd .. && ./gradlew :shift:check)
(cd .. && ./gradlew :shift:update :shift:generateJooq)
(cd ../.. && scripts/new-migration.sh shift <slug>)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
