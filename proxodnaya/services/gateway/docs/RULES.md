# RULES — gateway (для AI-агентов)

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём сервисе; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `proxodnaya/docs/API.md` + `./gradlew :gateway:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа и безопасности — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `proxodnaya/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Маршрут нового сервиса — одна запись в `RoutesConfig`/`application.yml` + тест маршрута.
- Каждый новый публичный путь без токена — явный список + тест «без токена → 401 для всего остального».

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite).
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh gateway <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, токенов, фото и эмбеддингов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Пропускать `/internal/**` наружу или открывать порты сервисов в compose.
- ⛔ Принимать решения о доступе по ролям вместо сервиса и пробрасывать «доверенные» заголовки пользователя (`X-User-Id`).
- Бизнес-логику в фильтрах, агрегацию ответов нескольких сервисов (BFF не делаем).

## Библиотеки
Разрешены: Spring Cloud Gateway, spring-security, Resilience4j/Bucket4j, Micrometer. Запрещены: Redis (лицензия), Zuul, сервлетный стек (gateway на WebFlux). Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- 401 без тела-утечки (какой issuer не подошёл — только в лог). 429 с `Retry-After`. 413 для больших кадров.

## Ограничения
Gateway не должен падать от недоступности одного upstream: таймаут маршрута + 503 problem+json `UPSTREAM_UNAVAILABLE`.
