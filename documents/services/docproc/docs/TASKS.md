# TASKS — docproc

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| DP-01 | NFR-15 | `new-service.sh docproc 8082 B2`, Liquibase: jobs, stage_events, stage_outputs, outbox | B2 | todo |
| DP-02 | FR-20 | `JobClaimer` (SKIP LOCKED + lease), `LeaseKeeper`, `WorkerPool` — TDD: `ConcurrentClaimTest` | B2 | todo |
| DP-03 | FR-20/26 | `StageMachine` + `stage_outputs`; `RestartMidStageTest` (kill посреди стадии → продолжение) | B2 | todo |
| DP-04 | FR-20 | Консьюмер `DocumentReceived`/`FileReplaced` → jobs (идемпотентно) | B2 | todo |
| DP-05 | FR-21 | Стадия EXTRACTING_TEXT: intake → ocr, UNREADABLE по метрикам качества | B2 | todo |
| DP-06 | FR-22 | `doc_types` + 8 схем (DOC-TYPES), `Classifier` через `LlmGateway`, `TYPE_UNKNOWN` | B2 | todo |
| DP-07 | FR-23 | `FieldExtractor` по JSON Schema, перегенерация, `MODEL_INVALID_OUTPUT` | B2 | todo |
| DP-08 | FR-24 | Нормализация и проверки: даты, MRZ, СНИЛС, ИНН, срок действия — тест на каждую | B2 | todo |
| DP-09 | B-02 | `ConfidenceScorer` (ADR-011), порог → NEEDS_REVIEW | B2 | todo |
| DP-10 | FR-26 | WAITING_MODEL, backoff, FAILED; тест «модель остановлена → поднята → дозавершилось» | B2 | todo |
| DP-11 | FR-31/43 | `parse_results`, `fields`, `llm_calls`; `DocumentParsed` с версиями промпта/схемы | B2 | todo |
| DP-12 | FR-29 | Vision-путь при слабом OCR (`VISION_FALLBACK_ENABLED`) + замер в EVAL | B2 | todo |
| DP-13 | FR-30/46 | `POST /documents/{id}/reprocess`, `GET /model/status`, результат для review | B2 | todo |
| DP-14 | FR-07 | Разметка страниц многостраничного файла по документам | B2 | todo |
| DP-15 | NFR-07 | Метрики `docproc_stage_seconds{stage}`, `docproc_jobs{stage}`, источник для B-06 | B2 | todo |
| DP-16 | FR-28 | Калибровка порогов и весов уверенности на открытом наборе, отчёт в EVAL.md | B2 | todo |
