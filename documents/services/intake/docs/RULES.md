# RULES — intake (для AI-агентов)

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём модуле; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `documents/docs/API.md` + `./gradlew :intake:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа, проверок и очереди — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `documents/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Каждая проверка файла — отдельный класс `*Check` + тест с реальным «плохим» файлом из `src/test/resources/files/` (синтетика, не ПДн).
- Тип файла — по сигнатуре байт (Tika `detect(bytes)`), расширение и `Content-Type` клиента — только для сообщения.
- Набор документов — только из `doc_requirements`; новое правило — миграция с данными + тест.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite); MinIO/S3 для файлов.
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh intake <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, содержимого файлов, распознанного текста и токенов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Передавать `slotType`, имя файла или порядок загрузки в событие для классификации (ADR-006).
- ⛔ Хранить файл на диске/в tmp дольше запроса или в открытом виде в БД.
- ⛔ Возвращать ключ заявки повторно или логировать его; хранить ключ не хешем.
- ⛔ PyMuPDF, poppler, ghostscript, iText (AGPL/GPL, DECISIONS).

## Библиотеки
Разрешены: Apache Tika core, PDFBox, TwelveMonkeys ImageIO, JCA. Запрещены: iText, ghostscript, MinIO. Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Отказ приёма — 4xx с кодом из таблицы API.md (`FILE_EMPTY`, `FILE_TOO_LARGE`, `FILE_TYPE_UNSUPPORTED`, `FILE_CORRUPTED`, `PDF_ENCRYPTED`, `TOO_MANY_PAGES`, `IMAGE_TOO_SMALL`, `DUPLICATE_FILE`, …); исключение парсера файла → `FILE_CORRUPTED`, никогда 500.
- Неверный/чужой ключ заявки → 404 (не раскрываем существование).

## Ограничения
Приём — p95 ≤ 300 мс: никакого OCR/модели в запросе. Тяжёлые проверки (открытие PDF) — с лимитом времени 5 с и памяти (PDFBox `MemoryUsageSetting.setupTempFileOnly` запрещён — только память с лимитом).
