# AGENTS.md — сервис review (Java 21, Spring Boot 3.5) — рабочее место оператора

Этот файл важнее `documents/AGENTS.md` для всего внутри `services/review/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B3. **Порт:** 8083. **БД:** `review`. Очередь оператора, сверка с оригиналом, правки, решение по заявке, история, сверка между документами, выгрузки, метрики (С13–С18 + бонусы B-03…B-08).

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, handoff, конфликты).

## Команды
```bash
# из services/review/
(cd .. && ./gradlew :review:check)
(cd .. && ./gradlew :review:bootRun)        # нужен infra/ (docker compose up -d postgres kafka keycloak)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
