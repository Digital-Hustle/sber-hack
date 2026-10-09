# RULES — libs/llm (для AI-агентов)

Сверху действуют `../../../../rules.md` (красные линии), `../../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём модуле; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `documents/docs/API.md` + `./gradlew :libs:llm:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа, проверок и очереди — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `documents/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Новый провайдер — новый пакет `infra/<name>` + тест на WireMock + строка в `../../../docs/LLM.md`.
- Тесты адаптеров — WireMock с записанными ответами формата API (не реальные документы).
- Новая модель в Ollama — сначала лицензия в карточке и строка в DECISIONS.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite); MinIO/S3 для файлов.
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh llm <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, содержимого файлов, распознанного текста и токенов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Ключей API в коде, тестах, логах; логирования промптов с текстом документа.
- ⛔ Fallback-ответов с данными при недоступности модели — только исключение `LlmUnavailable`.
- ⛔ Моделей с некоммерческой лицензией (`qwen2.5vl:3b` и т. п.).
- Бизнес-логики (типы документов, поля) в библиотеке — только транспорт и гарантии.

## Библиотеки
Разрешены: Jackson, networknt json-schema-validator (Apache-2.0), Resilience4j, WireMock (тесты). Запрещены: SDK провайдеров с несовместимой лицензией; LangChain4j — только через ADR. Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Сеть/5xx/таймаут → `LlmUnavailable` (docproc → WAITING_MODEL); 4xx авторизации → `LlmMisconfigured` (видно в `status()`); невалидный JSON дважды → `LlmInvalidOutput`.

## Ограничения
Ollama на CPU — 1–2 параллельных вызова (`LLM_MAX_CONCURRENCY`); `keep_alive=30m`, чтобы модель не выгружалась между документами.
