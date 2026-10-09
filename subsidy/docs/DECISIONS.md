# Журнал решений (ADR-lite) — «Субсидии»

Формат: `ADR-NNN | контекст | решение | альтернативы | последствия`. Унаследованные от «Проходной» — `← P-ADR-NNN` (`../../proxodnaya/docs/DECISIONS.md`), от «Документов» — `← D-ADR-NNN` (`../../documents/docs/DECISIONS.md`); там же полный текст. Новое значимое решение (технология, формат, отказ от функции, лицензия) — новая запись в конец.

### ADR-001 Микросервисы как в «Проходной» ← P-ADR-015, 017, 024
Решение: gateway + package, docproc, ocr (Python), rules, assistant; БД и роль на сервис в одном PostgreSQL; Kafka + outbox. Альтернатива: монолит — проще, но теряем подготовленный процесс и скиллы. Срез scope: assistant вливается в rules, если не успеваем.

### ADR-002 Очередь разбора — таблица `jobs` в PostgreSQL ← D-ADR-002
Kafka доставляет «файл принят», воркеры берут `FOR UPDATE SKIP LOCKED` с lease, стадии и промежуточные результаты в БД. Закрывает Д4 и СН «данные в памяти».

### ADR-003 OCR — отдельный Python-сервис ← D-ADR-003
`pdfplumber` (текст и таблицы), `pypdfium2` (рендер), OpenCV, Tesseract `rus+eng`; DOCX/XLSX — Apache POI в docproc. Решения — в Java.

### ADR-004 Модель за `LlmGateway` в `libs/llm`, локально Ollama ← P-ADR-005, D-ADR-004
Библиотека в docproc и assistant; `qwen3:8b` (текст) + `qwen2.5vl:7b` (vision), Apache-2.0; выбор подтверждается замером (LLM.md).

### ADR-005 Тип — по содержимому, с сегментацией многодокументных файлов
Контекст: 25 баллов и СН «тип по имени/порядку/расширению». Решение: в событие для docproc не передаются имя, путь в архиве и порядок; модель получает страницы и реестр видов, возвращает сегменты `[страницы → вид, уверенность, обоснование]`; код проверяет покрытие страниц. Альтернатива: «один файл = один документ» — проваливает склеенные сканы. Последствия: тест «переименовали и перемешали — те же типы», `guard.sh`.

### ADR-006 Модель извлекает, правила — детерминированный код
Модель только читает (тип и поля); комплектность, реквизиты, даты, арифметика — Java-код в `rules` с тестами на числа (Д11). Параметры программы (ставка, лимит, сроки, ОКВЭД) — данные `programs` с версией. Альтернатива: «спросить модель, всё ли в порядке» — недетерминированно и необъяснимо.

### ADR-007 Деньги — `BigDecimal`, в JSON строками
`numeric(19,2)`, `RoundingMode.HALF_UP` только в `Money`, допуск сравнения 0.01; в API деньги — строки. Альтернатива: `double` — копеечные ошибки в правилах.

### ADR-008 Протокол — неизменяемый снимок с версиями
Каждая перепроверка создаёт новую версию `protocols` (снимок входных данных, версии чек-листа, правил, промптов). Экспорт и история строятся из снимков → воспроизводимо и объяснимо.

### ADR-009 Правки специалиста — данные поверх результата модели
`field_overrides` и смена типа хранятся отдельно от результата модели (`source=SPECIALIST`), правила пересчитывает сервер. Фронт никогда не присылает статусы правил.

### ADR-010 Файлы в PostgreSQL, AES-256-GCM ← P-ADR-009, D-ADR-007
`bytea` до 30 МБ, ключ из `.env`, nonce на версию файла. MinIO — AGPL.

### ADR-011 ZIP — распаковка на сервере с лимитами
Лимиты размера, числа записей, без вложенных архивов, без `..`/абсолютных путей; каждый файл — отдельная запись `files`; имя файла хранится только для отображения.

### ADR-012 SSE для стадий и протокола ← P-ADR-008

### ADR-013 Дизайн-система и токены — общие с «Проходной» ← P-ADR-031, 033, 036

### ADR-014 Помощник — инструменты + валидатор чисел (как assistant «Проходной»)
Модель выбирает инструменты чтения (`get_protocol`, `get_fields`, `find_anomalies`), ответ только по их результатам, числа сверяет валидатор; расхождение — пометка в ответе. Помощник ничего не меняет.

### ADR-015 Процесс агентов: draft PR, handoff, локальный CI, мерж по делегированию ← P-ADR-014, 034, 035, 037

### ADR-016 Превью страниц рендерит package (PDFBox / ImageIO), кэш в `page_previews`
Превью работает без ocr: не нужен сетевой хоп и передача расшифрованного файла. ocr `/v1/render` остаётся для тяжёлых сканов. PDFBox — Apache-2.0, в разрешённом списке.

## Лицензии зависимостей
| Зависимость | Лицензия | Кто добавил |
|---|---|---|
| Spring Boot / Spring Cloud Gateway / Spring Kafka | Apache-2.0 | B1 |
| PostgreSQL / Keycloak / Apache Kafka | PostgreSQL / Apache-2.0 / Apache-2.0 | B1 |
| jOOQ (OSS) / Liquibase / MapStruct / Resilience4j | Apache-2.0 | B1 |
| Gradle / checkstyle / ArchUnit | Apache-2.0 / LGPL-2.1 (инструмент) / Apache-2.0 | B1 |
| Jaeger / OpenTelemetry | Apache-2.0 | B1 |
| Apache Tika core, Apache Commons Compress (ZIP) | Apache-2.0 | B1 |
| FastAPI / uvicorn / pydantic | MIT / BSD / MIT | B2 |
| pdfplumber / pdfminer.six / pypdfium2 | MIT / MIT / Apache-2.0, BSD-3 | B2 |
| opencv-python-headless / numpy / Pillow / pillow-heif | Apache-2.0 / BSD / MIT-CMU / BSD-3 | B2 |
| Tesseract 5 + tessdata `rus`, `eng` / pytesseract | Apache-2.0 | B2 |
| networknt json-schema-validator | Apache-2.0 | B2 |
| Ollama / Qwen3 / Qwen2.5-VL-7B | MIT / Apache-2.0 / Apache-2.0 | B3 |
| Apache POI / Apache PDFBox / TwelveMonkeys ImageIO | Apache-2.0 / Apache-2.0 / BSD-3 | B3 |
| React / Tailwind / shadcn / Radix / TanStack / zustand / i18next / motion | MIT | F1/F2 |
| oidc-client-ts / pdfjs-dist / react-dropzone / recharts | Apache-2.0 / Apache-2.0 / MIT / MIT | F1/F2 |
| @tanstack/react-query | MIT | F2 |
| Liberation Sans (шрифт в PDF-экспорте протокола rules) | SIL OFL 1.1 | B3 |
| Остальное фронта (компоненты реестров, иконки, шрифты) | как в `../../proxodnaya/docs/DECISIONS.md` | F2 |

**Запрещено (п. 5.8.4):** PyMuPDF/fitz (AGPL), pdf2image + poppler-utils (GPL), ghostscript (AGPL), Camelot (тянет ghostscript), iText (AGPL), MinIO (AGPL), Qwen2.5-VL-3B (research), любые веса «non-commercial».