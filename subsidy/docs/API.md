# Контракт API — «Субсидии» (черновик, финализировать на синке Т0+0:45)

Все запросы идут через gateway: `/<service>/api/v1/...` (`package`, `docproc`, `rules`, `assistant`); gateway переписывает путь в `/api/v1/...` сервиса (как в «Проходной»). Коллекции — `{items: [...], page: {next, total}}`, request — `*Rq`, response — `*Rs`. Внутренние вызовы — `/internal/**`, наружу gateway их не пропускает. Ошибки — `application/problem+json`: `{type, title, status, detail, code, hint, traceId, errors?}` (`detail`/`hint` — по-русски). Курсоры, `options`, SSE, `Idempotency-Key` — `../../.agents/skills/backend-spring/references/frontend-contract.md`. Деньги в JSON — **строки** `"12345.67"` (без потерь точности), даты — `YYYY-MM-DD`.

## Аутентификация
OIDC через Keycloak (`https://auth.<host>/realms/subsidy`, клиент `subsidy-specialist`, PKCE), `Authorization: Bearer <JWT>`. Роли: `SPECIALIST` (пакеты, загрузка, правки, протокол), `SENIOR_SPECIALIST` (+ решения, программы, переразбор; TOTP), `AUDITOR` (только чтение протоколов, истории, статистики). SSE: `GET /<svc>/api/v1/stream/ticket` → одноразовый `ticket` на 30 с.

## package (`/package/api/v1`)
- `GET /organizations?inn=` → организация или 404; `POST /organizations` `{inn}` → 201 (ИНН проверяется контрольной суммой, `INN_INVALID`)
- `POST /packages` `{orgInn, programCode}` → 201 `{packageId, status: COLLECTING}` (`Idempotency-Key`)
- `GET /packages?status=&inn=&program=&cursor=` → очередь: `{id, org, program, status, completeness, failed, warnings, needsReview, updatedAt}`
- `POST /packages/{id}/files` multipart `files[]` (файлы или ZIP) → **202** `{files: [{fileId, stage: RECEIVED, duplicateOf?}], rejected: [{name, code, detail, hint}]}` — отказ одного файла не отменяет остальные
- `POST /packages/{id}/files/{fileId}/versions` multipart `file` → 202 (замена / повторная загрузка, FR-65)
- `DELETE /packages/{id}/files/{fileId}` → 204
- `GET /packages/{id}` → `{status, org, program, files: [{fileId, version, mime, pages, stage, reasonCode?, documents: [{documentId, fromPage, toPage, type, typeConfidence, source, stage}]}]}`
- `GET /files/{fileId}/content?version=` → оригинал (роль + аудит); `GET /files/{fileId}/pages/{n}.png` → превью страницы
- `GET /stream?packageId=&ticket=` (SSE, билет из `GET /stream/ticket`) — `file.stage`, `document.parsed`, `package.checked`; heartbeat `: ping` каждые 15 с
- `POST /packages/{id}/decision` `{decision: APPROVE|REJECT|RETURN, comment}` → только `SENIOR_SPECIALIST`, только при наличии протокола

## docproc (`/docproc/api/v1`)
- `GET /documents/{id}/result` → `{type, typeConfidence, evidence, fields: [{name, value, raw, page, bbox, confidence, checks}], model, promptVersion, schemaVersion}`
- `PUT /documents/{id}/type` `{type, reason}` → 202 (переизвлечение полей по новому типу, FR-29)
- `POST /files/{fileId}/reprocess` → 202 (`SENIOR_SPECIALIST`)
- `GET /model/status` → `{provider, model, visionModel, reachable, lastLatencyMs}`
- `GET /doc-types` → реестр видов (код, название, поля)

## rules (`/rules/api/v1`)
- `GET /packages/{id}/protocol` → последний протокол: `{protocolId, version, status: COMPLETE|INCOMPLETE|NEEDS_REVIEW, checklistVersion, rulesVersion, completeness: {items: [{item, type, condition, status, documents[]}], missing: [{type, reason}]}, results: [{rule, status, message, evidence: [{documentId, field, page, bbox?}], expected?, actual?}], createdAt}`
- `GET /packages/{id}/protocols` → версии протоколов (история перепроверок)
- `PUT /packages/{id}/fields` `{documentId, field, value, reason}` → 202 — правка значения специалистом (данные, не вердикт); сервер перепроверяет и создаёт новую версию протокола
- `GET /packages/{id}/protocol/export?format=docx|xlsx|pdf` → файл (B-02)
- `GET /organizations/{inn}/history` → `[{at, by, action, packageId, details}]` (B-03)
- `GET /stats/errors?from=&to=&program=` → `[{rule, count, share}]` + «как посчитано» (B-04)
- `GET /expiring?days=30` → документы, срок которых истекает (B-05)
- `GET /programs`, `GET /programs/{code}` → параметры и чек-лист; `PUT /programs/{code}` — только `SENIOR_SPECIALIST`, новая версия

## assistant (`/assistant/api/v1`)
- `POST /packages/{id}/ask` `{question}` → `{answer, sources: [{documentId, field?, rule?}], table?, validator: {status: OK|NUMBERS_MISMATCH, details}}` (B-06; ответ только из инструментов, числа сверяет валидатор)

## Стадии файла / документа
| stage | Текст специалисту |
|---|---|
| RECEIVED | Файл принят |
| QUEUED | В очереди на разбор (позиция N) |
| EXTRACTING_TEXT | Читаем текст |
| CLASSIFYING | Определяем, какие документы в файле |
| EXTRACTING_FIELDS | Извлекаем данные |
| VALIDATING | Проверяем реквизиты |
| PARSED | Разобран |
| NEEDS_REVIEW | Разобран, есть что проверить (тип или поля под вопросом) |
| UNREADABLE | Не удалось прочитать — см. причину |
| WAITING_MODEL | Модель недоступна, продолжим автоматически |
| FAILED | Не удалось разобрать — можно «разобрать заново» |

## Коды отказов приёма (package, HTTP 4xx)
| code | HTTP | Текст | Подсказка |
|---|---|---|---|
| FILE_EMPTY | 400 | Файл пустой | Выберите файл ещё раз |
| FILE_TOO_LARGE | 413 | Файл больше {max} МБ | Разделите документ или уменьшите разрешение скана |
| FILE_TYPE_UNSUPPORTED | 415 | Такой формат не поддерживается | Подойдут PDF, JPG, PNG, TIFF, HEIC, DOCX, XLSX, ZIP |
| FILE_CORRUPTED | 422 | Файл повреждён и не открывается | Сохраните документ заново |
| PDF_ENCRYPTED | 422 | PDF защищён паролем | Снимите пароль и загрузите снова |
| TOO_MANY_PAGES | 422 | Слишком много страниц ({max}) | Разделите файл |
| ZIP_UNSAFE | 422 | Архив не принят: {reason} | Упакуйте файлы заново без вложенных архивов |
| DUPLICATE_FILE | 409 | Этот файл уже есть в пакете | Загрузите другой документ |
| TOO_MANY_FILES | 422 | В пакете уже {max} файлов | Удалите лишние |
| INN_INVALID | 400 | ИНН не прошёл проверку контрольной суммы | Проверьте цифры ИНН |
| PACKAGE_DECIDED | 409 | По пакету уже принято решение | Создайте новый пакет |
| RATE_LIMITED | 429 | Слишком много загрузок подряд | Подождите минуту |

## Причины непригодности и замечания (в результате, не HTTP)
| reasonCode | Текст |
|---|---|
| IMAGE_BLURRY / IMAGE_TOO_DARK / IMAGE_CUT_OFF | Скан размыт / слишком тёмный / обрезан — переснимите |
| TEXT_NOT_FOUND | Не удалось найти текст |
| TYPE_UNKNOWN | Не удалось уверенно определить вид документа — выберите вид вручную |
| TYPE_OTHER | Документ не входит в перечень: {summary} |
| CHECKSUM_FAILED | {field} не прошёл проверку контрольной суммы |
| DUPLICATE_DOCUMENT | Такой документ уже есть в пакете (файл {other}) |
| MODEL_UNAVAILABLE | Модель недоступна, продолжим автоматически |
| MODEL_INVALID_OUTPUT | Модель вернула ответ не по схеме — проверьте вручную |

Коды правил и тексты — `RULES-CATALOG.md`.

## Внутренние API (только service accounts, `aud=<сервис>`)
- package: `GET /internal/package/files/{fileId}?version=` → поток (только `svc-docproc`)
- ocr: `POST /v1/extract` multipart `file` → `{pages: [{n, width, height, textLayer, text, words: [{t, bbox, conf}], tables, quality: {blur, brightness, cutOff, dpi}}], durationMs}`; `POST /v1/render?page=` → PNG
- docproc: `GET /internal/docproc/packages/{id}/documents` → все результаты пакета (для rules)
- rules: `GET /internal/rules/packages/{id}/facts` → протокол + поля (для assistant, только чтение)