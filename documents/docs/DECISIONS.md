# Журнал решений (ADR-lite) — «Документы»

Формат: `ADR-NNN | контекст | решение | альтернативы | последствия`. Решения, унаследованные от «Проходной», отмечены `← P-ADR-NNN` (`../../proxodnaya/docs/DECISIONS.md`); там же их полный текст. Добавляем запись при каждом значимом выборе: технология, зависимость (с лицензией), отступление от плана.

### ADR-001 Микросервисы как в «Проходной» ← P-ADR-015, 017, 024
Контекст: команда и агенты уже подготовлены под этот каркас (скиллы, скрипты, CI). Решение: gateway + intake, docproc, ocr (Python), review; БД и роль на сервис в одном PostgreSQL; Kafka + outbox. Альтернатива: монолит — проще, но теряем подготовленный процесс и параллельность пяти человек. Последствия: срез scope — слить review в intake, если к Т+3:00 не собирается (PLAN.md).

### ADR-002 Очередь разбора — таблица `jobs` в PostgreSQL, а не Kafka
Контекст: Д7 и снятие («работа теряется при перезапуске», «данные только внутри очереди»). Решение: Kafka доставляет «файл принят», потребитель идемпотентно создаёт `jobs`; воркеры берут `FOR UPDATE SKIP LOCKED` с lease; стадии и промежуточные результаты в БД. Альтернативы: Kafka как очередь заданий (нет видимости стадий, переобработка при ребалансе), Spring `@Async` (теряется при рестарте). Последствия: состояние видно SQL, метрики очереди — запрос к `stage_events`.

### ADR-003 OCR — отдельный Python-сервис, решения — в Java ← P-ADR-025
Решение: `ocr` на FastAPI: `pdfplumber` (MIT) для текстового слоя, `pypdfium2` (Apache-2.0/BSD) для рендера, OpenCV (Apache-2.0) для предобработки, Tesseract 5 (Apache-2.0) `rus+eng`. Классификация и извлечение — в docproc (Java) через `LlmGateway`. Альтернативы: PyMuPDF (AGPL — запрещено), pdf2image/poppler (GPL — запрещено), PaddleOCR (Apache-2.0, точнее на фото, но тяжелее — включаем вторым движком, если Tesseract не тянет на открытом наборе, ADR при включении).

### ADR-004 Модель за `LlmGateway` в `libs/llm`, три адаптера, локально Ollama ← P-ADR-005
Решение: библиотека, а не сервис: один вызов на стадию без лишнего сетевого прыжка; изоляция — ArchUnit. Локально `qwen3:8b` (текст) + `qwen2.5vl:7b` (vision), обе Apache-2.0. Выбор подтверждается замером (LLM.md, FR-42).

### ADR-005 Гибрид: модель извлекает, код проверяет
Контекст: «разбор реально моделью» (Д5) + точность на закрытом наборе. Решение: модель определяет тип и извлекает поля по JSON Schema; детерминированные проверки (MRZ, контрольные суммы, даты) подтверждают/понижают уверенность и нормализуют формат, но не подставляют значения вместо модели. Альтернатива: только регулярки — нарушает Д5; только модель — теряем точность и не видим ошибок.

### ADR-006 Тип не зависит от имени файла и пункта загрузки
Решение: в событие для docproc имя файла не передаётся; `slotType` используется только после классификации — чтобы сообщить «перенесли в нужный пункт». Последствия: проверяется `guard.sh` и тестом «переименованный файл → тот же тип».

### ADR-007 Файлы в PostgreSQL, зашифрованы AES-256-GCM ← P-ADR-009
Решение: `bytea` (до 20 МБ) с шифрованием на уровне приложения (ключ из `.env`, nonce на файл). Альтернатива: MinIO (AGPL), диск (риск «файл рядом с кодом»), SeaweedFS (Apache-2.0, лишняя движущаяся часть).

### ADR-008 Соискатель без пароля: ключ заявки
Решение: 256-битный ключ в ссылке (в БД — `sha256`), короткий код возврата + контакт для другого устройства; область — одна заявка. Альтернативы: Keycloak-аккаунт соискателя (трение, пароль), SMS-OTP (нет шлюза на хакатоне). Последствия: IDOR-тест обязателен.

### ADR-009 SSE для стадий и очереди ← P-ADR-008

### ADR-010 Набор документов — данные (`doc_requirements`), а не код
Решение: правила `condition jsonb → doc_type` в БД intake; анкета и пересчёт набора на сервере. Последствия: смена кейса/набора = миграция данных.

### ADR-011 Уверенность поля — смесь модели, OCR и проверок
Решение: формула в ARCHITECTURE.md, веса в настройках, калибровка на открытом и своём «закрытом» наборах (EVAL.md). Альтернатива: только самооценка модели — плохо калибрована.

### ADR-012 Дизайн-система и токены — общие с «Проходной» ← P-ADR-031, 033, 036
Решение: тот же `tokens.ts` и генератор темы, своё — только компоненты и зоны (`frontend/docs/DESIGN.md`).

### ADR-013 Процесс агентов: draft PR, handoff, локальный CI, мерж по делегированию ← P-ADR-014, 034, 035, 037

### ADR-014 Доступ к БД — Spring JdbcClient вместо jOOQ codegen; сборка образов внутри docker
jOOQ codegen требует живую БД во время сборки — тогда `docker compose up --build` с чистого клона невозможен (Д1). Берём `JdbcClient` (Spring, SQL как есть, параметры биндятся), схема — Liquibase formatted SQL, миграции применяются при старте сервиса. `services/Dockerfile` собирает jar внутри образа (multi-stage). Цена: нет типобезопасных таблиц — компенсируем интеграционными тестами на настоящем PostgreSQL (CI service container).

### ADR-015 Профиль checkstyle на хакатоне
Форматирование, импорты, отступы, длина строки, пустые catch, фигурные скобки, структура классов — ошибкой сборки. Стилистические метрики (javadoc на каждом методе, минимальная длина имён, «магические» числа, повтор строковых литералов, метрики сложности) — подавлены в `config/checkstyle/checkstyle-suppressions.xml`: их смотрит ревью, а не сборка. Правила `be-check.mjs` и `guard.sh` — без ослаблений.

### ADR-016 Keycloak под путём /auth того же хоста
Один сертификат и один origin для SPA, API и SSO: `https://<host>/auth` (`KC_HTTP_RELATIVE_PATH=/auth`, `KC_HOSTNAME=https://<host>/auth`). Сервисы проверяют подпись по JWKS из внутренней сети (`http://keycloak:8080/...`), а `iss` — по публичному адресу. Service accounts `svc-docproc`, `svc-review` получают роли `SERVICE_DOCPROC`/`SERVICE_REVIEW`, ими закрыты `/internal/**`.

## Лицензии зависимостей
| Зависимость | Лицензия | Кто добавил |
|---|---|---|
| Spring Boot / Spring Cloud Gateway / Spring Kafka | Apache-2.0 | B1 |
| PostgreSQL | PostgreSQL | B1 |
| Keycloak | Apache-2.0 | B1 |
| Apache Kafka | Apache-2.0 | B1 |
| jOOQ (OSS) / Liquibase / MapStruct / Resilience4j | Apache-2.0 | B1 |
| Gradle / checkstyle / ArchUnit | Apache-2.0 / LGPL-2.1 (инструмент) / Apache-2.0 | B1 |
| Jaeger / OpenTelemetry | Apache-2.0 | B1 |
| Apache Tika (только детект типа по сигнатуре, `tika-core`) | Apache-2.0 | B1 |
| FastAPI / uvicorn / pydantic | MIT / BSD / MIT | B2 |
| pdfplumber / pdfminer.six | MIT | B2 |
| pypdfium2 (PDFium) | Apache-2.0 / BSD-3 | B2 |
| opencv-python-headless / numpy / Pillow | Apache-2.0 / BSD / MIT-CMU | B2 |
| Tesseract 5 + tessdata `rus`, `eng` / pytesseract | Apache-2.0 | B2 |
| pillow-heif (libheif) | BSD-3 (libheif LGPL, динамически) | B2 |
| networknt json-schema-validator | Apache-2.0 | B2 |
| Ollama / Qwen3 / Qwen2.5-VL-7B | MIT / Apache-2.0 / Apache-2.0 | B3 |
| Apache POI / Apache PDFBox | Apache-2.0 | B3 |
| React / Tailwind / shadcn / Radix / TanStack / zustand / i18next / motion | MIT | F1/F2 |
| oidc-client-ts | Apache-2.0 | F2 |
| react-dropzone / react-pdf (pdf.js) | MIT / MIT (pdf.js Apache-2.0) | F1/F2 |
| Остальное фронта (компоненты реестров, иконки, шрифты) | как в `../../proxodnaya/docs/DECISIONS.md` | F2 |

**Запрещено (п. 5.8.4):** PyMuPDF/fitz (AGPL), pdf2image + poppler-utils (GPL), ghostscript (AGPL), iText (AGPL), MinIO (AGPL), ClamAV в составе образа (GPL), Qwen2.5-VL-3B (research), любые веса «non-commercial».
