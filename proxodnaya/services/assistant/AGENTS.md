# AGENTS.md — сервис assistant (Java 21, Spring Boot)

Этот файл важнее `proxodnaya/AGENTS.md` для всего внутри `services/assistant/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B3. **Порт:** 8084. **БД:** `assistant`. Помощник руководителя на LLM: числа считает сервер, модель только формулирует.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом сервисе.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` + `../../../.agents/skills/proxodnaya-dev/references/llm-assistant.md` и процесс `../../docs/WORKFLOW.md` (draft PR, handoff, конфликты).

## Команды
```bash
# из services/assistant/
(cd .. && ./gradlew :assistant:check)
(cd ../../infra && LLM_PROVIDER=ollama docker compose --profile llm-local up -d ollama)
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```

## Главное
Каждое число в ответе должно встречаться в результатах инструментов (`NumberValidator`). Заготовленный ответ = снятие (Д5).
