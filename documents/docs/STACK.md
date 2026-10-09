# Стек — «Документы»

Тот же каркас, что у «Проходной» (`../../proxodnaya/docs/STACK.md`): одна команда `docker compose up`, разрешённые лицензии, то, что агенты знают. Ниже — только отличия и добавления.

## Backend (Java) — скилл `backend-spring`
Java 21, Spring Boot 3.5.x, Spring Cloud Gateway, Gradle Kotlin DSL (wrapper, version catalog, convention plugins `documents.*` — `init-backend.sh services` из папки `documents/`), jOOQ + Liquibase, MapStruct + Lombok, `RestClient` + Resilience4j, Kafka 3.9 (KRaft) + outbox, Micrometer Tracing → Jaeger, springdoc → `contracts/openapi/<svc>.json`, checkstyle, ArchUnit, JUnit 5 + Testcontainers.
| Добавлено | Зачем | Лицензия |
|---|---|---|
| `tika-core` | тип файла по сигнатуре (magic bytes), не по расширению | Apache-2.0 |
| PDFBox | число страниц, шифрование PDF, выгрузка карточки в PDF | Apache-2.0 |
| Apache POI | XLSX/DOCX выгрузки (истекающие документы, карточка для кадров) | Apache-2.0 |
| networknt json-schema-validator | проверка ответа модели по схеме полей | Apache-2.0 |
| JCA AES-GCM | шифрование файлов в БД | — |

## Backend (Python) — сервис ocr
| Что | Выбор | Лицензия |
|---|---|---|
| Рантайм | Python 3.12, `uv` | PSF / MIT |
| API | FastAPI + uvicorn + pydantic v2 | MIT / BSD |
| PDF | `pdfplumber` (текстовый слой + координаты), `pypdfium2` (рендер страниц) | MIT / Apache-2.0 |
| Изображения | `opencv-python-headless`, `Pillow`, `pillow-heif` | Apache-2.0 / MIT-CMU / BSD |
| OCR | Tesseract 5 (`apt install tesseract-ocr tesseract-ocr-rus`) + `pytesseract`; запасной — PaddleOCR (ADR при включении) | Apache-2.0 |
| MRZ | свой парсер ICAO 9303 + проверка контрольных цифр (≈100 строк, тесты) | — |
| Качество | ruff, mypy --strict, pytest | MIT |
**Нельзя**: PyMuPDF/fitz (AGPL), pdf2image + poppler (GPL), ghostscript, wand/ImageMagick-обёртки для PDF (тянут ghostscript).

## Frontend — скилл `frontend-fsd`
Одно Vite-приложение, React 19, TS strict, npm, FSD; Tailwind 4 + shadcn/ui; axios `$api`, TanStack Query, типы из `openapi-typescript`; zustand; react-hook-form + zod; i18next; React Router (зоны `/apply`, `/operator`); вход операторов — своя форма «логин + код» (ADR-020); `react-dropzone` (загрузка), камера через `<input capture>` + `getUserMedia`; `react-pdf` (pdf.js) и свой просмотрщик изображений с зумом и подсветкой bbox; recharts (метрики очереди).

## LLM
| Режим | Провайдер | Модель |
|---|---|---|
| Облако (если дадут ключ) | GigaChat API | GigaChat-2 Pro / Max (для vision — GigaChat с картинками, если доступно) |
| Облако-альтернатива | OpenAI-совместимый | любой с JSON mode |
| **Локально (Д6)** | Ollama (профиль `llm-local`) | `qwen3:8b` текст, `qwen2.5vl:7b` vision |
Переключение — `LLM_PROVIDER` в `.env`.

## Инфраструктура
`docker compose`: `caddy`, `frontend`, `gateway`, `intake`, `docproc`, `ocr`, `review`, `keycloak`, `postgres`, `kafka`, `jaeger`, `ollama` + `ollama-pull` (профиль `llm-local`), `mailpit` (MIT, для B-01 «письмо соискателю», опционально). HTTPS обязателен (камера телефона). Секреты — `infra/.env` из `scripts/gen-env.sh`.

## Сознательно НЕ берём
RabbitMQ/Redis, Kubernetes, MinIO/S3, векторные БД (поиск не нужен), LangChain-подобные фреймворки (лишний слой, сложнее объяснить), PyMuPDF/poppler (лицензии), облачные OCR (ПДн уходят наружу; Д6 требует локальной работы).
