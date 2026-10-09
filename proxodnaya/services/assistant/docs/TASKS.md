# TASKS — assistant

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| AS-01 | NFR-15 | `new-service.sh assistant 8084 B3`, миграции llm_requests/insights | B3 | review #75 (llm_requests; insights — с AS-10) |
| AS-02 | FR-40/41 | `LlmGateway` + адаптер Ollama, compose-профиль `llm-local`, docs/LLM.md | B3 | review #75 (OpenAI-совместимый адаптер, Ollama через `/v1`; docs/LLM.md нет) |
| AS-03 | FR-40 | Адаптер GigaChat (OAuth, кэш токена, сертификат НУЦ) | B3 | todo |
| AS-04 | FR-42 | Инструменты: на объекте сейчас, часы, опоздания/переработки, отказы | B3 | review #75 (аналитика shift токеном спросившего) |
| AS-05 | FR-43 | `NumberValidator` + перегенерация + отказ с сырыми данными, `NumberValidatorTest` | B3 | review #75 |
| AS-06 | FR-44 | `sources` и `table` в ответе | B3 | todo |
| AS-07 | FR-45/46 | Таймаут 30 с, CB, журнал `llm_requests`, `/assistant/status` | B3 | review #75 (таймаут — на вызов модели) |
| AS-08 | FR-47 | ArchUnit: SDK только в `llm.infra`; `PromptInjectionTest` | B3 | todo |
| AS-09 | FR-40 | Адаптер OpenAI-совместимый | B3 | review #75 (один адаптер на Ollama и OpenAI) |
| AS-10 | B-09 | Аномалии каждые 15 мин правилами + сводка моделью, `assistant.insight.v1` | B3 | todo |
