# PRD — ocr

Продукт целиком — `../../../docs/PRD.md`, требования с ID — `../../../docs/REQUIREMENTS.md`. Здесь — только этот модуль.

## Обзор
Внутренний сервис предобработки для docproc: PDF с текстовым слоем → текст как есть; скан/фото → рендер, поворот, выравнивание, контраст, Tesseract `rus+eng`; MRZ-зона; метрики качества (резкость, яркость, блики, обрезка). Отдаёт JSON, ничего не хранит.

## Проблемы
- Фото с телефона: поворот по EXIF, наклон, тени, блики — модель на «сыром» снимке ошибается.
- Нужно честно отличать «нечитаемо» от «плохо распознали».
- Лицензии: PyMuPDF/poppler/ghostscript (AGPL/GPL) нельзя.

## Цели
1. Текст + слова с bbox и уверенностью для каждой страницы.
2. Метрики качества с порогами → причины UNREADABLE (`IMAGE_BLURRY`, `IMAGE_TOO_DARK`, `IMAGE_GLARE`, `IMAGE_CUT_OFF`, `TEXT_NOT_FOUND`).
3. MRZ найден и разобран (строки + контрольные цифры) для паспортов.
4. Страница A4 на CPU ≤ 5 с.

## Пользователи
- docproc — `POST /v1/extract` с service-account JWT (`aud=ocr`).

## Ключевые функции
- `POST /v1/extract` (файл, mime) → `{pages:[{text, words[{text,bbox,conf}], quality{blur,brightness,glare,cutoff}, size}], mrz?, textLayer: bool}`.
- PDF: `pdfplumber` (текстовый слой), рендер `pypdfium2`.
- Изображения: Pillow + `pillow_heif`, OpenCV: EXIF-поворот, deskew, CLAHE, бинаризация.
- Tesseract 5 `rus+eng` через `pytesseract` (`image_to_data`).
- MRZ: поиск зоны (морфология OpenCV) + OCR с whitelist `A-Z0-9<` + разбор TD3/TD1.

## MVP
| Обязательно (P0, к интеграции №1–2) | Потом (P1/P2, если есть время) |
|---|---|
| текстовый слой PDF, рендер, EXIF-поворот, Tesseract с bbox, метрики резкости/яркости | deskew, блики/обрезка, MRZ, предпросмотр страниц для оператора (PNG) |

Задачи и статусы — `TASKS.md`.
