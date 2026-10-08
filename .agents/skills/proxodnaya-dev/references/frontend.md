# Frontend — специфика «Проходной»

Общие правила FSD (слои, импорты, данные, UI, маршруты) — **скилл `frontend-fsd`** (`.agents/skills/frontend-fsd/SKILL.md`), адаптация под кейс — `frontend-fsd/references/adaptation.md`. Здесь только напоминания.

- Одно приложение `frontend/` с зонами `/kiosk` (F1), `/worker` (F1), `/admin` (F2). `shared`, `entities`, `app` — ревью F2.
- Решение о проходе не считается на клиенте: киоск показывает `decision / code / message / hint` из ответа access.
- Данные — только через сгенерированный API-клиент + TanStack Query, без ручного `fetch`. MSW — только в dev.
- worker: ключ устройства `crypto.subtle.generateKey({name:'ECDSA', namedCurve:'P-256'}, false, ['sign'])` в IndexedDB; QR `v1.<deviceId>.<window>.<sig>` подписывается на телефоне и работает без интернета.
- kiosk: камера через `getUserMedia` (нужен HTTPS), сканирование QR, активный челлендж живости, баннер «Нет связи», офлайн-режим = ручное подтверждение охранника.
- Каждый экран: loading / empty / error («Повторить») / data. Всё на русском, крупные кнопки (киоск ≥ 64px, телефон ≥ 48px).

## Команды
```bash
cd frontend
npm ci && npm run gen:api
npm run dev
npm run lint && npm run typecheck && npm run build
node ../../.agents/skills/frontend-fsd/scripts/fsd-check.mjs src
```
