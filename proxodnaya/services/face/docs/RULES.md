# RULES — face (для AI-агентов)

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём сервисе; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `proxodnaya/docs/API.md` + `./gradlew :face:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа и безопасности — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `proxodnaya/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Порог и параметры — из конфига/настроек, в коде только имена.
- Каждый отказ — свой `reason`, совпадающий с кодами `docs/API.md`.
- Тесты на синтетике или фото команды с согласия, лежащих вне git (путь из env).

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite).
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh face <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, токенов, фото и эмбеддингов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Принимать решение о проходе или возвращать ALLOW — только факты.
- ⛔ Хранить фото, логировать кадры/эмбеддинги, отдавать эмбеддинги наружу.
- ⛔ InsightFace, buffalo_l, antelopev2 и любые веса с non-commercial лицензией; dlib/face_recognition без проверки лицензий моделей.
- Поиск 1:N, pgvector/векторные БД без ADR.

## Библиотеки
Разрешены: onnxruntime (MIT), opencv-python-headless (Apache-2.0), numpy, FastAPI, pydantic, cryptography, psycopg. Модели: YuNet (MIT), SFace (Apache-2.0), MiniFASNet (Apache-2.0). Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Ошибки API — problem+json так же, как в Java-сервисах (`code`, `detail`). Неверные кадры → 422 с `errors[]`; внутренняя ошибка инференса → 500 → access трактует как `SYSTEM_ERROR`.

## Ограничения
Сверка ≤ 1 с на 3–5 кадров на CPU. Один воркер uvicorn на ядро; модели грузятся при старте (readiness до загрузки — not ready).
