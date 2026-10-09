# TASKS — ocr

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| OCR-01 | FR-21 | Каркас FastAPI, JWT `aud=ocr`, health, Dockerfile с tesseract-ocr-rus, CI-джоба python | B2 | todo |
| OCR-02 | FR-21 | PDF: текстовый слой (pdfplumber) с bbox; рендер страниц pypdfium2 | B2 | todo |
| OCR-03 | FR-21 | Предобработка: EXIF-поворот, deskew, CLAHE, авто-поворот по OSD | B2 | todo |
| OCR-04 | FR-21 | Tesseract `rus+eng`: слова + bbox + conf по странице | B2 | todo |
| OCR-05 | FR-25 | Метрики качества: резкость, яркость, блики, обрезка; тесты на синтетике | B2 | todo |
| OCR-06 | FR-24 | MRZ: поиск зоны, OCR с whitelist, разбор TD3/TD1 + контрольные цифры | B2 | todo |
| OCR-07 | FR-52 | Предпросмотр страниц PNG для просмотрщика оператора (`/v1/render`) | B2 | todo |
| OCR-08 | NFR-03 | Метрики времени по этапам, лимиты страниц и времени | B2 | todo |
