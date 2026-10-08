# AGENTS.md — сервис access (Java 21, Spring Boot)

Этот файл важнее `proxodnaya/AGENTS.md` для всего внутри `services/access/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B2. **Порт:** 8082. **БД:** `access`. **Единственное место, где принимается решение о проходе** (Д2).

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом сервисе.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` и процесс `../../docs/WORKFLOW.md` (draft PR, handoff, конфликты).

## Команды
```bash
# из services/access/
(cd .. && ./gradlew :access:check)        # unit + IT (Testcontainers) + checkstyle + ArchUnit
(cd .. && ./gradlew :access:update :access:generateJooq)
(cd ../.. && scripts/new-migration.sh access <slug>)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```

## Инварианты (нарушение = риск снятия)
- Гашение `(device_id, window)` + `pass_attempts` + anti-passback + outbox — **одна транзакция**.
- Любая ошибка/таймаут зависимости → `DENY SYSTEM_ERROR` (fail-closed).
- Сверка лица 1:1 с рабочим из QR, не 1:N. Журнал append-only.
