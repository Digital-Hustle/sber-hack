# LLM и помощник (подробно: docs/LLM.md)

- Сервис: `services/assistant`. Интерфейс: `LlmGateway#chat(ChatRq) → ChatRs` (messages, tools, toolCalls, usage).
- Реализации в `gateway/llm/{gigachat,openai,ollama}`; выбор — `app.llm.provider`. Новый провайдер = новый адаптер + `@ConditionalOnProperty`, больше ничего не меняется.
- GigaChat: OAuth по `GIGACHAT_AUTH_KEY` (scope из env), токен живёт 30 мин — кэшируй; TLS требует корневой сертификат НУЦ Минцифры (кладётся в truststore образа, не в git, если он не публичный).
- Помощник: `AssistantCommandService` (features/assistant/command) — цикл tool calling, максимум 3 итерации.
- Инструменты — `features/assistant/application/tools`, каждый: имя, JSON-схема аргументов, read-only вызов `/internal/**` shift-service или access-service через service-токен, результат JSON. Добавляешь инструмент → добавь его в `docs/LLM.md` и тест.
- `NumberValidator`: извлечь числа из ответа → сверить с множеством чисел из результатов инструментов (с нормализацией 12,5 / 12.5 / проценты, пересчитанные сервером) → `ok | regenerated | blocked`.
- Запрещено: заготовленные ответы, fallback-текст с числами, прямой SQL от модели, передача модели ПДн сверх необходимого (только ФИО и агрегаты).
- Проверка локально: `LLM_PROVIDER=ollama docker compose --profile llm-local up -d`, затем вопросы из `docs/LLM.md`.
