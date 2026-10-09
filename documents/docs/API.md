# Контракт API — «Документы» (черновик, финализировать на синке Т0+0:45)

Все запросы идут через gateway: `/<service>/api/v1/...` (`intake`, `docproc`, `review`); gateway переписывает путь в `/api/v1/...` сервиса (как в «Проходной»). Коллекции — в обёртке `{items: [...], page: {next, total}}`, request — `*Rq`, response — `*Rs`. Внутренние вызовы — `/internal/**`, наружу gateway их не пропускает. Ошибки — `application/problem+json`: `{type, title, status, detail, code, hint, traceId, errors?}` (`detail`/`hint` — по-русски для человека). Формат курсоров, `options`, SSE, `Idempotency-Key` — `../../.agents/skills/backend-spring/references/frontend-contract.md`.

## Аутентификация
- **Оператор**: OIDC через Keycloak (`https://auth.<host>/realms/documents`, клиент `documents-operator`, PKCE), `Authorization: Bearer <JWT>`, роли `OPERATOR`, `SENIOR_OPERATOR`, `AUDITOR`.
- **Соискатель**: ключ заявки. `POST /intake/api/v1/applicant/applications` создаёт заявку и возвращает `{applicationId, accessKey, resumeCode}` один раз. Дальше `Authorization: Applicant <accessKey>`; gateway пропускает, intake проверяет `sha256(accessKey)` и что `applicationId` в пути — та же заявка (иначе 404). `POST /intake/api/v1/applicant/resume` `{phoneOrEmail, resumeCode}` → новый `accessKey` (5 попыток, затем 429).
- **SSE**: `GET /<svc>/api/v1/stream/ticket` → `{ticket}` одноразовый на 30 с для `EventSource` (заголовки в EventSource не передаются).

## Соискатель (intake: `/intake/api/v1/applicant/...`)
- `GET /applicant/questionnaire` → вопросы анкеты `{questions: [{id, text, kind: single|multi|bool, options}]}`
- `POST /applicant/applications` `{consentPd: true, contact: {phone?, email?}}` → 201 `{applicationId, accessKey, resumeCode, link}`; без согласия → 400 `CONSENT_REQUIRED`
- `PUT /applicant/applications/{id}/profile` `{answers}` → `{requiredDocuments: [{type, title, hint, required, condition}]}` — набор считает сервер по `doc_requirements` (FR-10)
- `POST /applicant/applications/{id}/documents` multipart `file`, `slotType?` → **202** `{documentId, fileId, stage: RECEIVED, duplicateOf?, warnings[]}` (`Idempotency-Key` обязателен)
- `POST /applicant/applications/{id}/documents/{docId}/files` — дозагрузка страницы/замена (`mode: add|replace`) → 202
- `DELETE /applicant/applications/{id}/documents/{docId}` → 204 (до решения оператора)
- `GET /applicant/applications/{id}` → `{status, profile, requiredDocuments, documents: [{id, slotType, detectedType, stage, reasonCode?, message?, hint?, updatedAt}], missing: [type], operatorRequests: [...]}` — **без извлечённых значений полей** (минимум ПДн в публичной зоне)
- `GET /applicant/applications/{id}/stream` (SSE, ticket) — `document.stage`, `application.request`, `application.decision`
- `POST /applicant/applications/{id}/submit` → `{status: SUBMITTED, missing[]}`

## Оператор
**intake** (`/intake/api/v1/operator/...`, роли OPERATOR+):
- `GET /files/{fileId}/content?page=` → поток файла/страницы (`Cache-Control: no-store`, просмотр → аудит); соискателю доступен тот же путь только для своих файлов
- `GET /operator/applications/{id}/documents` → документы и версии файлов

**docproc** (`/docproc/api/v1/...`, OPERATOR+):
- `GET /documents/{id}/result` → `{type, typeConfidence, stage, fields: [{name, title, value, raw, page, bbox, confidence, checks: [{code, ok}], reason?}], model, promptVersion, durations: {stage: ms}}`
- `POST /documents/{id}/reprocess` (SENIOR_OPERATOR) → 202 — новый job, старый результат хранится (FR-30)
- `GET /model/status` → `{provider, model, visionModel?, reachable, checkedAt}`
- `GET /queue/stats` → `{waiting, inProgress, parsed, needsReview, unreadable, waitingModel, avgSecondsByStage, p95Seconds}` (B-06)

**review** (`/review/api/v1/...`):
- `GET /applications?status&q&assignee&sort=waiting|updated&cursor&limit` → очередь (номера документов маскированы, B-07)
- `POST /applications/{id}/assign` → назначить себе; занято другим → 409 `ALREADY_ASSIGNED`
- `GET /applications/{id}` → карточка: документы, результаты (через docproc), правки, замечания `findings[]`, история
- `PATCH /documents/{id}/fields` `{fields: [{name, value}]}` → правки с историей (FR-53)
- `PATCH /documents/{id}/type` `{type}` → смена типа → переизвлечение полей по новой схеме (job `EXTRACTING_FIELDS`)
- `POST /documents/{id}/confirm`; `POST /applications/{id}/request` `{items: [{type?, documentId?, reason}]}` → соискателю (FR-55, B-01)
- `POST /applications/{id}/decision` `{decision: ACCEPTED|RETURNED, comment}`
- `GET /applications/{id}/history` (B-08) · `GET /applications/{id}/export?format=docx|xlsx|pdf` (B-05, SENIOR_OPERATOR; PDF — PDFBox + встроенный Liberation Sans)
- `GET /reports/expiring?days=30`, XLSX — `GET /reports/expiring/export?days=30` (SENIOR_OPERATOR) (B-04) · `GET /metrics` (B-06, с формулами «как посчитано») · `POST /applications/next` — взять следующую свободную · `GET /stream` (SSE, ticket): `queue.changed`, `document.stage`
- `POST /applications/{id}/reveal` `{field}` → полный номер документа + запись в аудит (B-07)

## Стадии документа
| stage | Текст соискателю | Видит оператор |
|---|---|---|
| RECEIVED | Файл принят | — |
| QUEUED | В очереди на разбор | позиция в очереди |
| EXTRACTING_TEXT | Читаем текст | — |
| CLASSIFYING | Определяем, что это за документ | — |
| EXTRACTING_FIELDS | Извлекаем данные | — |
| VALIDATING | Проверяем данные | — |
| PARSED | Документ разобран | поля, уверенность |
| NEEDS_REVIEW | Документ разобран, его проверит оператор | поля «перепроверить» сверху |
| UNREADABLE | Не удалось прочитать — см. причину | причина, метрики качества |
| WAITING_MODEL | Разбор временно приостановлен, продолжим автоматически | причина сбоя модели, сколько ждёт |
| FAILED | Не удалось разобрать, оператор проверит вручную | ошибка, «разобрать заново» |

## Коды отказов приёма (intake, HTTP 4xx)
| code | HTTP | Текст | Подсказка |
|---|---|---|---|
| FILE_EMPTY | 400 | Файл пустой | Выберите файл ещё раз |
| FILE_TOO_LARGE | 413 | Файл больше {max} МБ | Сожмите фото или загрузите PDF меньшего размера |
| FILE_TYPE_UNSUPPORTED | 415 | Такой формат не поддерживается | Подойдут PDF, JPG, PNG, HEIC, TIFF |
| FILE_CORRUPTED | 422 | Файл повреждён и не открывается | Сохраните или снимите документ заново |
| PDF_ENCRYPTED | 422 | PDF защищён паролем | Снимите пароль или загрузите фото документа |
| TOO_MANY_PAGES | 422 | Слишком много страниц ({max}) | Загрузите только страницы нужного документа |
| IMAGE_TOO_SMALL | 422 | Слишком маленькое изображение | Снимите ближе, весь документ в кадре |
| DUPLICATE_FILE | 409 | Этот файл уже загружен ({title}) | Загрузите другой документ |
| TOO_MANY_FILES | 422 | В заявке уже {max} файлов | Удалите лишние файлы |
| RATE_LIMITED | 429 | Слишком много запросов подряд (gateway: создание заявки 20/10 мин, вход по коду 10/15 мин, загрузки 120/мин на IP; заголовок `Retry-After`) | Подождите минуту |
| CONSENT_REQUIRED | 400 | Нужно согласие на обработку данных | Отметьте согласие |
| IDEMPOTENCY_KEY_REQUIRED | 400 | Нет заголовка Idempotency-Key | Обновите страницу и повторите |
| RESUME_INVALID | 400 | Код или контакт не подходят | Проверьте код из ссылки-приглашения |
| APPLICATION_LOCKED | 409 | Заявка уже на проверке у оператора | Дождитесь ответа оператора |

## Коды ошибок оператора и служебные (review, docproc, HTTP)
| code | HTTP | Текст | Подсказка |
|---|---|---|---|
| VALIDATION_ERROR | 400 | Неверный ответ анкеты (intake) | Ответьте на вопрос |
| QUEUE_EMPTY | 404 | Свободных заявок нет | Новые появятся в очереди автоматически |
| ALREADY_ASSIGNED | 409 | Заявку уже проверяет {оператор} | Выберите другую заявку |
| NOT_PARSED_YET | 409 | Документ ещё не разобран | Дождитесь окончания разбора |
| NOT_SUBMITTED | 409 | Соискатель ещё не отправил заявку | Заявка появится в очереди после отправки |
| APPLICATION_DECIDED | 409 | По заявке уже принято решение | Изменения после решения не принимаются |
| DOCUMENTS_IN_PROGRESS | 409 | Не все документы разобраны | Дождитесь окончания разбора |
| COMMENT_REQUIRED | 400 | Укажите причину возврата | Соискатель увидит этот текст |
| DECISION_UNKNOWN | 400 | Решение — ACCEPTED или RETURNED | — |
| DOCUMENT_NOT_IN_APPLICATION | 400 | Документ не из этой заявки | Обновите карточку |
| FIELD_UNKNOWN | 400 | В документе нет поля «{name}» | Обновите карточку |
| CURSOR_INVALID | 400 | Неверный курсор | Начните с первой страницы |
| FORMAT_UNSUPPORTED | 400 | Формат — docx, xlsx или pdf | Укажите format=docx, xlsx или pdf |
| INTAKE_UNAVAILABLE | 503 | Сервис приёма недоступен (внутренний вызов) | Повторите позже — разбор продолжится сам |
| DOCPROC_UNAVAILABLE | 503 | Сервис разбора недоступен (внутренний вызов) | Повторите позже |
| OCR_UNAVAILABLE | 503 | Сервис распознавания недоступен (внутренний вызов docproc → ocr) | Разбор повторится автоматически |

Сверка «код в коде ↔ строка в этом файле» — `scripts/check-contracts.py` (CI, job security).

## Причины непригодности и замечания (docproc, в результате — не HTTP-ошибки)
| reasonCode | Текст | Подсказка |
|---|---|---|
| IMAGE_BLURRY | Снимок размыт | Держите телефон неподвижно, наведите фокус |
| IMAGE_TOO_DARK | Слишком темно | Снимите при хорошем свете, без вспышки |
| IMAGE_GLARE | Блик закрывает текст | Наклоните документ, уберите блик |
| IMAGE_CUT_OFF | Документ обрезан | Весь документ должен быть в кадре |
| NOT_A_DOCUMENT | На файле нет документа | Загрузите фото или скан документа |
| TEXT_NOT_FOUND | Не удалось найти текст | Загрузите более чёткий снимок |
| TYPE_UNKNOWN | Не удалось определить вид документа | Оператор проверит вручную; можно указать пункт |
| TYPE_MOVED | Это другой документ — перенесли в пункт «{type}» | — |
| DOCUMENT_EXPIRED | Срок действия документа истёк {date} | Загрузите действующий документ |
| CHECKSUM_FAILED | Номер не прошёл проверку ({field}) | Оператор сверит с оригиналом |
| MRZ_MISMATCH | Данные в машиночитаемой строке не совпадают с текстом | Оператор сверит с оригиналом |
| CROSS_MISMATCH | {field} отличается в документах «{a}» и «{b}» | Оператор уточнит |
| NUMBER_ELSEWHERE | Номер из документа «{doc}» уже есть в другой заявке (заявок: {count}) | Проверьте, не повторная ли это подача или чужой документ |
| MULTI_DOCUMENT | В одном файле несколько документов: здесь стр. {pages}, а {documents} — отдельными пунктами | Мы разделили файл сами — проверьте, что каждый документ попал в свой пункт (FR-07: intake создаёт по документу на каждый найденный вид, `sourceDocumentId` + `pages`) |
| MODEL_UNAVAILABLE | Модель недоступна | Продолжим автоматически |
| MODEL_INVALID_OUTPUT | Модель вернула ответ не по схеме | Оператор проверит вручную |

## Внутренние API (только service accounts, `aud=<сервис>`)
Снаружи их нет: gateway отвечает 404 на любой путь с сегментом `internal` (до проверки токена).
- intake: `GET /internal/intake/files/{fileId}` → поток (только `svc-docproc`); `GET /internal/intake/applications/{id}/summary` (для review)
- ocr: `POST /v1/extract` multipart `file` → `{pages: [{n, width, height, textLayer: bool, text, words: [{t, bbox, conf}], quality: {blur, brightness, cutOff, glare, dpi}}], mrz?: {raw, fields, checks}, durationMs}`; `POST /v1/render?page=` → PNG страницы (для vision-пути)
- docproc: `GET /internal/docproc/documents/{id}/result` (для review)
