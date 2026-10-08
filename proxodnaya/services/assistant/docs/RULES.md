# RULES — assistant (для AI-агентов)

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём сервисе; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `proxodnaya/docs/API.md` + `./gradlew :assistant:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа и безопасности — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `proxodnaya/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Новый инструмент: имя, JSON-схема, read-only вызов, тест; описание инструмента — для модели, коротко.
- Числа, которые нужны в ответе (проценты, разницы), считает сервер в инструменте, а не модель.
- Проверяй локальный режим: `LLM_PROVIDER=ollama` с выключенным интернетом.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite).
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh assistant <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, токенов, фото и эмбеддингов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Заготовленные ответы, fallback-текст с числами, `mock/stub/fake` в `src/main` (guard.sh).
- ⛔ Прямой SQL или запись от имени модели; инструменты только читают.
- ⛔ SDK провайдеров вне `llm.infra` (ArchUnit); ключи в коде/логах.
- Передавать модели ПДн сверх ФИО и агрегатов; отдавать сырой ответ модели без валидатора.

## Библиотеки
Разрешены: HTTP через `RestClient` (предпочтительно), официальные SDK под Apache/MIT только в `llm.infra`. Модели локально: Qwen3 (Apache-2.0) через Ollama (MIT). Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Таймаут/CB открыт → 200 `{text: «Помощник сейчас недоступен…», validation: UNAVAILABLE, sources: []}` без чисел; ошибка валидатора → `validation: REFUSED` + сырые данные инструментов таблицей.

## Ограничения
≤ 3 итераций tool calling, 30 с на ответ, вопрос ≤ 500 символов. GigaChat требует сертификат НУЦ Минцифры в truststore образа.
