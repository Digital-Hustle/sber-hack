# Конвейер разбора (30 + 10 баллов) — подробно `docs/ARCHITECTURE.md §Конвейер`, `docs/DOC-TYPES.md`, `docs/EVAL.md`

1. `DocumentReceived` → `jobs` (`on conflict do nothing`). Стадии: RECEIVED → QUEUED → EXTRACTING_TEXT → CLASSIFYING → EXTRACTING_FIELDS → VALIDATING → PARSED | NEEDS_REVIEW | UNREADABLE (+ WAITING_MODEL, FAILED).
2. Текст: ocr `/v1/extract` (слой PDF или Tesseract), метрики качества → UNREADABLE с `reasonCode` до вызова модели.
3. Классификация: промпт `classify.v<N>` + реестр `doc_types`, только содержимое; ниже `CLASSIFY_MIN_CONFIDENCE` → `TYPE_UNKNOWN`.
4. Извлечение: `extract.v<N>` + JSON Schema вида; значение только из текста, иначе `null`; невалидно → перегенерация → `MODEL_INVALID_OUTPUT`.
5. Проверки: нормализация, MRZ (ICAO 9303), СНИЛС, ИНН, даты, срок действия → `checks[]`; уверенность (ADR-011) → NEEDS_REVIEW при поле ниже `FIELD_REVIEW_THRESHOLD`.
6. Результат с `model`, `prompt_version`, `schema_version`; outbox `DocumentParsed`.

## Качество без подгонки
- Изменил промпт/схему/предобработку/порог → `cd eval && uv run python run.py --set synthetic` (+ open у B2) → строка в отчёте в PR.
- Few-shot — только `eval/synthetic/`. Никаких значений и имён файлов открытого набора в коде и промптах (`guard.sh`).
- Ошибки разбираем по категориям (поворот, блик, разворот, латиница), фиксы — общие.
