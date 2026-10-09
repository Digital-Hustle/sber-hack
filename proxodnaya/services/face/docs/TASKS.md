# TASKS — face

Формат — `../../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #41` → `done #PR` | `blocked: …`. Статус меняется в ветке задачи вместе с кодом. Новая задача — строкой в конец, ID не переиспользуем. Порядок строк = приоритет. Межмодульные задачи и вехи — `../../../docs/TASKS.md`.

| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FACE-01 | FR-04 | Скелет FastAPI + uv + ruff/mypy/pytest, health/readiness, загрузка моделей по sha256 | B2 | review #41 |
| FACE-02 | FR-05 | Качество кадра (размер, резкость, яркость), ровно одно лицо — YuNet | B2 | review #41 |
| FACE-03 | FR-13/66 | Enroll: эмбеддинг SFace, AES-256-GCM, `face_templates`, миграция | B2 | review #41 |
| FACE-04 | FR-04 | Verify 1:1: тот же человек во всех кадрах, косинус ≥ порога | B2 | review #41 |
| FACE-05 | FR-06 | Живость: челлендж по yaw из landmarks в окне 10 с | B2 | review #41 |
| FACE-06 | FR-06 | MiniFASNet антиспуфинг (P2) | B2 | todo |
| FACE-07 | NFR-10 | Удаление эталонов рабочего, авторизация service account (`aud=face`) | B2 | review #41 |
| FACE-08 | FR-04 | Калибровка порога на фото команды, таблица FAR/FRR для слайда | B2 | todo |
| FACE-09 | NFR-07 | Метрики `face_score`, latency (есть), OTel-трейсинг | B2 | todo |
| FACE-10 | FR-06 | BLINK: моргание по 68 точкам или EAR — сейчас access не выдаёт BLINK | B2 | todo |
