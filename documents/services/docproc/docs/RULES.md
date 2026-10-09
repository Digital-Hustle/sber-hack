# RULES — docproc (для AI-агентов)

Сверху действуют `../../../rules.md` (красные линии), `../../../docs/WORKFLOW.md` и скилл `backend-spring`. ⛔ — PR не принимается.

## Делай
- Работай только в своём модуле; чужой — через его API (`/internal/**`), события или PR с ревью владельца. Общие части (`libs/common`, `build-logic`, `gradle/libs.versions.toml`) — владелец B1.
- Сначала контракт: `documents/docs/API.md` + `./gradlew :docproc:exportOpenApi` (REST) или `contracts/events/` (события), отдельным маленьким PR.
- Раскладка пакетов, контроллер-интерфейс + impl, Rq/Rs, MapStruct, jOOQ, Liquibase — по скиллу `backend-spring` (`be-check` проверяет).
- Тест вместе с кодом; для правил доступа, проверок и очереди — сначала тест (red → green).
- Время — только через внедрённый `Clock`; пользователь — только из JWT (`CurrentUser`).
- Перед PR: `documents/scripts/ci-local.sh quick`, перед ready — `full`; статус в `docs/TASKS.md`, состояние и журнал в `docs/MEMORY.md`.
- Изменил промпт, схему, предобработку или пороги → прогон `eval/run.py` и строка в EVAL-отчёте в том же PR (хранитель качества — B2).
- Каждая стадия идемпотентна и восстанавливается из `stage_outputs`; новый переход — тест на рестарт.
- Модель вызывается только через `LlmGateway`; в промпт идут текст и признаки документа, никогда имя файла и пункт.
- Пороги и веса — в конфиге/`settings`, калибруются на открытом наборе по метрике, а не под конкретный файл.

## Избегай
- ⛔ Моков, заглушек, «временных» ответов и захардкоженных данных в боевом пути (rules.md §1) — feature-флаг `false` + честная ошибка.
- ⛔ Данных в памяти процесса или файлах вместо PostgreSQL; встраиваемых БД (H2/SQLite); MinIO/S3 для файлов.
- ⛔ Чужой БД (`jdbc:…/<чужой сервис>`), публикации в Kafka мимо outbox, контекста пользователя из заголовков/ThreadLocal.
- ⛔ Правки смерженных changeset-ов Liquibase — только новая миграция `scripts/new-migration.sh docproc <slug>`.
- ⛔ Отключения тестов, checkstyle, ArchUnit, проверок CI «чтобы прошло».
- Логирования ПДн, содержимого файлов, распознанного текста и токенов; `Instant.now()`; `permitAll()` кроме health/openapi.
- ⛔ Определять тип по имени файла, расширению, пункту загрузки или порядку (СН).
- ⛔ Регулярки/шаблоны вместо модели как основной путь извлечения (Д5); регулярки — только проверка результата модели.
- ⛔ Заготовленные ответы, fallback с данными, few-shot из открытого набора; подгонку под конкретные файлы (rules.md §1.3).
- ⛔ Итоговую стадию без записи `llm_calls` (кроме UNREADABLE до модели).
- Импорт SDK провайдеров модели (ArchUnit `LlmIsolationTest`).

## Библиотеки
Разрешены: `libs/llm`, networknt json-schema-validator, Resilience4j. Запрещены: прямые SDK OpenAI/GigaChat/Ollama (только в `libs/llm`), LangChain4j как обязательная зависимость (решить ADR, если понадобится). Новая зависимость — только с разрешённой лицензией (без GPL/AGPL, п. 5.8.4), строка в `../../../docs/DECISIONS.md`, версия в `gradle/libs.versions.toml` (B1) или `pyproject.toml`.

## Обработка ошибок
- Ошибки API — `application/problem+json` через `libs/common` (`ProblemDetail` + `ErrorCode`): `code`, `detail` по-русски, `hint`, `errors[]` для полей (формат — `backend-spring/references/frontend-contract.md`). Stack trace наружу не отдаём.
- Валидация входа — Bean Validation на Rq → 400 с `errors[]`; нет прав → 403; чужой ресурс → 404 (не раскрываем существование).
- Вызовы других сервисов — `RestClient` + Resilience4j (таймаут, circuit breaker); отказ зависимости — понятный код, а не 500.
- Консьюмеры Kafka идемпотентны (`processed_events`), 3 повтора → `<topic>.dlt`; ошибка одного события не стопорит партицию.
- Ошибка стадии — не HTTP-ошибка: `attempts+1`, пауза, после `JOB_MAX_ATTEMPTS` → FAILED с `last_error_code`; модель недоступна → WAITING_MODEL без траты попыток.
- Ответ модели не по схеме → одна перегенерация с текстом ошибки, затем `MODEL_INVALID_OUTPUT` → NEEDS_REVIEW.

## Ограничения
Разбор документа на CPU ≤ 60 с (локальная модель); `LLM_MAX_CONCURRENCY` ограничивает одновременные вызовы. Текст документа не логируется и не уходит в метрики.
