# PRD — libs/llm

Продукт целиком — `../../../../docs/PRD.md`, требования с ID — `../../../../docs/REQUIREMENTS.md`. Здесь — только этот модуль.

## Обзор
Слой модели: единый интерфейс `LlmGateway` (`chat`, `structured(schema)`, `vision`, `status`), три адаптера (GigaChat, OpenAI-совместимый, Ollama), выбор провайдера настройкой, общие таймаут/повтор/CB/семафор, валидация JSON Schema, журнал вызовов. Даёт 15 баллов «модель и локальный запуск».

## Проблемы
- Судьи проверяют, что модель изолирована и меняется настройкой (Д5).
- Локальный провайдер должен реально работать без интернета (Д6).
- Модели отвечают невалидным JSON, тормозят и падают.

## Цели
1. Смена провайдера — только `LLM_PROVIDER` в `.env`, ноль правок кода.
2. Ответ `structured` валиден по схеме или понятное исключение (`LlmInvalidOutput`, `LlmUnavailable`).
3. Каждый вызов записан в `llm_calls` (через порт `LlmCallJournal` сервиса).
4. ArchUnit: SDK провайдеров — только в `ru.documents.llm.infra`.

## Пользователи
- docproc (классификация, извлечение, vision).
- B3/судьи — бейдж статуса модели.

## Ключевые функции
- `LlmGateway`, `ChatResult`, `LlmException` (Unavailable/Timeout/InvalidOutput).
- `OllamaAdapter` (`format: <schema>`, `keep_alive`, `/no_think`), `OpenAiCompatAdapter` (`response_format: json_schema`), `GigaChatAdapter` (OAuth, scope, функции).
- Resilience4j: timeout, retry ×2, circuit breaker; семафор `LLM_MAX_CONCURRENCY`.
- `SchemaValidator` (networknt) + одна перегенерация с ошибкой.
- Micrometer: `llm_latency_seconds{provider,model,op}`, `llm_errors_total`.
- Автоконфигурация Spring Boot `@ConditionalOnProperty(app.llm.provider)`.

## MVP
| Обязательно (P0, к интеграции №1–2) | Потом (P1/P2, если есть время) |
|---|---|
| интерфейс, Ollama + OpenAI-совместимый, structured + валидация, таймаут/повтор/CB, журнал, ArchUnit | GigaChat, vision, метрики, замер моделей (X-04) |

Задачи и статусы — `TASKS.md`.
