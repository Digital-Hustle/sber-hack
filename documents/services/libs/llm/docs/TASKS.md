# TASKS — libs/llm

Формат — `../../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| LLM-01 | FR-40 | Каркас библиотеки, `LlmGateway`, исключения, автоконфигурация, `LlmIsolationTest` | B3 | todo |
| LLM-02 | FR-40 | `GigaChatAdapter` (OAuth, scope) + WireMock-тест | B3 | todo |
| LLM-03 | FR-29 | `vision` для Ollama (qwen2.5vl) и OpenAI-совместимого | B3 | todo |
| LLM-04 | FR-40 | `ResilientLlmGateway`: timeout, retry, CB, семафор; `SchemaValidator` + перегенерация | B3 | todo |
| LLM-05 | FR-43 | `LlmCallJournal` + метрики; `status()` для `/model/status` | B3 | todo |
| LLM-06 | FR-41 | `OllamaAdapter` + `OpenAiCompatAdapter`; Ollama и `ollama-pull` в compose (с B1) | B3 | todo |
| LLM-07 | FR-41 | `infra/ollama/README.md`: офлайн-архив образа и моделей, инструкция для судей | B3 | todo |
| LLM-08 | FR-42 | Замер моделей на открытом наборе (с B2) → таблица в LLM.md | B3 | todo |
