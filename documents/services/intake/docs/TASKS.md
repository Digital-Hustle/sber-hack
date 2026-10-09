# TASKS — intake

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| IN-01 | NFR-15 | `new-service.sh intake 8081 B1`, БД, Liquibase: applicants, applications, documents, document_files, outbox | B1 | done `feat/IN-01-intake` |
| IN-02 | FR-14 | Создание заявки, ключ (hash), `ApplicationKeyFilter`, IDOR-тест «ключ A → заявка B = 404» | B1 | done `feat/IN-01-intake` |
| IN-03 | FR-10 | Анкета → `doc_requirements` → `requiredTypes`; данные для 8 видов (DOC-TYPES) | B1 | done `feat/IN-01-intake` |
| IN-04 | FR-01/05 | `FileInspector` + проверки (пустой, размер, сигнатура, целостность, пароль, страницы, разрешение) — TDD на битых файлах | B1 | done `feat/IN-01-intake` |
| IN-05 | FR-02/62 | Приём: AES-GCM + document + outbox в одной транзакции, `202`; `DocumentReceived` | B1 | done `feat/IN-01-intake` |
| IN-06 | FR-03 | Дубликаты sha256 (отказ) и pHash (предупреждение) | B1 | done `feat/IN-01-intake` |
| IN-07 | FR-04 | Дозагрузка страниц, замена, удаление; версии; `FileReplaced` → переразбор | B1 | done `feat/IN-01-intake` |
| IN-08 | FR-12 | Консьюмер `docproc.document.v1` → стадии, тип, `TYPE_MOVED`; SSE соискателю | B1 | done `feat/IN-01-intake` |
| IN-09 | FR-62 | `/internal/intake/files/{id}` (svc-docproc) и `/files/{id}/content` (роль + аудит) | B1 | done `feat/IN-01-intake` |
| IN-10 | FR-15/B-01 | Итог «комплект полный / не хватает X» по `requiredTypes` и определённым типам | B1 | done `feat/IN-01-intake` |
| IN-11 | FR-06 | Лимиты файлов и частоты, `TOO_MANY_FILES`, `RATE_LIMITED` | B1 | done `feat/IN-01-intake` |
| IN-12 | FR-07 | Многостраничный файл с несколькими документами: разметка страниц от docproc → документы | B1 | done #45 — intake создаёт документ на каждый вид из `split` (пункт = вид, файлы исходного, `only_pages`), идемпотентно; повторно не делится; замена/удаление исходного удаляет выделенные |
| IN-13 | FR-55 | `ResubmitRequested` от review → пункт «переснять» с причиной у соискателя | B1 | done `feat/IN-01-intake` |
