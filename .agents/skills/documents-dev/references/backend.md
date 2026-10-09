# Backend — специфика «Документов»

Общие правила (пакеты, контроллер + impl, Rq/Rs, jOOQ + Liquibase, outbox, Kafka, тесты, Python) — **скилл `backend-spring`**. Здесь только кейс. Подробно — `docs/ARCHITECTURE.md`, `services/<svc>/docs/`.

## Сервисы
| Сервис | Порт | Владелец | Суть |
|---|---|---|---|
| gateway | 8080 | B1 | JWT Keycloak + публичный путь соискателя, `/internal/**` наружу не пускает |
| intake | 8081 | B1 | заявки, анкета → набор (`doc_requirements`), приём файлов и **все серверные проверки**, дубликаты, версии, файлы AES-GCM, ключ заявки, SSE |
| docproc | 8082 | B2 | очередь `jobs`, стадии, классификация и извлечение через `LlmGateway`, проверки полей, уверенность |
| ocr | 8000 | B2 | Python/FastAPI: текстовый слой, рендер, предобработка, Tesseract, качество, MRZ |
| review | 8083 | B3 | очередь оператора, карточка, правки, история, сверка между документами, выгрузки, метрики |
| libs/llm | — | B3 | `LlmGateway` + адаптеры (подключается в docproc) |

Новый сервис: `.agents/skills/backend-spring/scripts/new-service.sh <svc> <port> <Owner>` (кейс определяется по `rootProject.name`), затем маршрут в gateway, контейнер в compose (без `ports:`), БД и роль, client в realm, строка в CI, CODEOWNERS.

## Инварианты
- Приём: файл + `documents` + outbox — одна транзакция, ответ `202` без OCR/модели. Тип файла — по сигнатуре (Tika), не по расширению.
- Имя файла и `slotType` не попадают в событие для docproc (ADR-006).
- Разбор — только из `jobs` (SKIP LOCKED + lease); каждая стадия идемпотентна, выход стадии в `stage_outputs`.
- Модель недоступна → `WAITING_MODEL`, не «готово». Итоговая стадия без `llm_calls` невозможна (кроме UNREADABLE до модели).
- `application_history` — append-only (триггер). Файлы оператору — только через intake с проверкой роли и аудитом.

## Обязательные тесты
intake: тест на каждый код отказа (битые файлы в `src/test/resources/files/`), IDOR ключа заявки. docproc: `ConcurrentClaimTest`, `RestartMidStageTest`, `WaitingModelTest`, тест каждой проверки поля (MRZ, СНИЛС, ИНН, даты), «переименованный файл → тот же тип». review: `RoleMatrixTest`, `HistoryAppendOnlyTest`, `CrossDocumentCheckerTest`. ocr: pytest на синтетике. Везде: `ArchitectureTest`, `LlmIsolationTest`.

## Команды
```bash
cd services
./gradlew :docproc:check
./gradlew :intake:update :intake:generateJooq
./gradlew exportOpenApi
node ../../.agents/skills/backend-spring/scripts/be-check.mjs .
cd ocr && uv run ruff check . && uv run mypy src && uv run pytest
```
