# AGENTS.md — сервис intake (Java 21, Spring Boot 3.5)

Этот файл важнее `documents/AGENTS.md` для всего внутри `services/intake/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B1. **Порт:** 8081. **БД:** `intake`. Приём и все серверные проверки входящих файлов (Д1), заявки, анкета → набор документов, хранение файлов (AES-GCM).

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом модуле.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, handoff, конфликты).

## Команды
```bash
# из services/intake/
(cd .. && ./gradlew :intake:check)
(cd .. && ./gradlew :intake:bootRun)        # нужен infra/ (docker compose up -d postgres kafka keycloak)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```
