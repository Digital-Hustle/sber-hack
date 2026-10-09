# Frontend — специфика «Документов»

Общие правила FSD — **скилл `frontend-fsd`**; модуль — `frontend/AGENTS.md`, `frontend/docs/`.

- Одно приложение `frontend/`, зоны `/apply` (F1, телефон-first) и `/operator` (F2, desktop). `shared`, `entities`, `app` — ревью F2.
- Соискатель: ключ заявки во фрагменте ссылки `#k=` → память; заголовок `Authorization: Applicant <key>`; не в localStorage.
- Проверки файла на клиенте — только подсказка; отказ и его текст — от intake. Тип и «не хватает» — из ответа сервера.
- Камера: `getUserMedia` / `<input capture>`, нужен HTTPS; снимок сжимается перед отправкой.
- Оператор: `DocumentViewer` (pdf.js, blob через `/files/{id}/content`) + `FieldsPanel` (уверенность, проверки, клик → bbox), горячие клавиши.
- Стадии и очередь — SSE + `invalidateQueries`. Каждый экран: loading / empty / error / offline.

```bash
cd frontend && npm ci && npm run gen:api && npm run dev
npm run lint && npm run typecheck && npm run build && node ../../.agents/skills/frontend-fsd/scripts/fsd-check.mjs src
```
