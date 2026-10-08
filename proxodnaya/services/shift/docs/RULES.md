# RULES — shift (для AI-агентов)

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём сервисе; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `proxodnaya/docs/API.md` + `./gradlew :shift:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа и безопасности — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `proxodnaya/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Любая цифра аналитики — SQL по реальным данным + текст «как посчитано» в ответе (`method`).
- Незакрытые/автозакрытые интервалы — отдельным полем (`incomplete`), не нулём и не «как будто вышел».
- `source=SIMULATOR` фильтруется параметром и по умолчанию исключён из отчётов.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite).
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh shift <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, токенов, фото и эмбеддингов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Заглушки и константы в аналитике (`guard.sh` ищет mock/stub/fake в `analytics/`).
- ⛔ Подмена данных: автозакрытие не равно выходу, флаг обязателен.
- Синхронные вызовы access из консьюмера; тяжёлые отчёты в транзакции консьюмера.

## Библиотеки
Разрешены: Apache POI (Apache-2.0). Запрещены: iText (AGPL), Grafana (AGPL), Redis. Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Ошибка обработки события → повтор → `access.pass.v1.dlt`, алерт в лог; интервалы догоняются после исправления (идемпотентно).

## Ограничения
Один инстанс (планировщики без распределённой блокировки). Временная зона площадки — из настроек, хранение в UTC.
