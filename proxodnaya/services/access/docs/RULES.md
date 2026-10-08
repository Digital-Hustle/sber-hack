# RULES — access (для AI-агентов)

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём сервисе; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `proxodnaya/docs/API.md` + `./gradlew :access:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа и безопасности — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `proxodnaya/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Новый код отказа: сначала строка в `docs/API.md`, тест в `DecisionEngineTest`, потом код.
- Всё, что успели посчитать (score, liveness, latency, какая зависимость упала), пишем в `details` попытки.
- Тест гонки гашения (`QrConsumeIntegrationTest`) должен оставаться зелёным при любых правках транзакции.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite).
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh access <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, токенов, фото и эмбеддингов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ ALLOW при ошибке/таймауте зависимости, «пропустим, раз face не ответил».
- ⛔ Вынос `qr_uses`/журнала/outbox из одной транзакции; UPDATE/DELETE `pass_attempts`.
- ⛔ Сверка 1:N (поиск похожего лица) вместо 1:1 с владельцем QR.
- Синхронный вызов people на проверку ключа устройства (есть проекция); долгие вызовы внутри транзакции.

## Библиотеки
Разрешены: Resilience4j, JCA/Bouncy Castle (MIT-подобная лицензия) для ECDSA. Запрещены: OpenCV/распознавание в Java (только face на Python). Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Отказ прохода — **не ошибка HTTP**: 200 `{decision: DENY, code, message, hint}`. HTTP-ошибки — только для неверного запроса/подписи киоска (`KIOSK_SIGNATURE_INVALID` → 401).

## Ограничения
Окно QR 30 с, допуск ±90 с (часы телефона). Кадров 3–5, окно челленджа 10 с. Ручной проход — только GUARD, с причиной, `source=MANUAL`.
