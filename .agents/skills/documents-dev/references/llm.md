# LLM-слой (подробно: docs/LLM.md)

- Библиотека `services/libs/llm`: `LlmGateway` (`chat`, `structured(schema)`, `vision`, `status`), адаптеры `infra/{ollama,openai,gigachat}`, выбор `app.llm.provider` (`LLM_PROVIDER`). Новый провайдер = новый пакет + `@ConditionalOnProperty` + WireMock-тест.
- Только HTTP-API провайдеров, без SDK; ArchUnit `LlmIsolationTest` — провайдеры только в `ru.documents.llm.infra`.
- Resilience: таймаут `LLM_TIMEOUT_SECONDS`, retry ×2, CB, семафор `LLM_MAX_CONCURRENCY`; `LlmUnavailable` → docproc `WAITING_MODEL`.
- Ollama: `format` = JSON Schema, `temperature=0`, `/no_think` для qwen3, `keep_alive=30m`. Локально: `LLM_PROVIDER=ollama docker compose --profile llm-local up -d`.
- GigaChat: OAuth по `GIGACHAT_AUTH_KEY`, токен 30 мин — кэшируй; сертификат НУЦ Минцифры — в truststore образа.
- Промпты — `docproc/src/main/resources/prompts/*.v<N>.md`, версия в результате. Запрещено: заготовленные ответы, fallback с данными, имя файла в промпте, некоммерческие веса.
