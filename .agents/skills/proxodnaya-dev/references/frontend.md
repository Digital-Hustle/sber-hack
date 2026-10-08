# Frontend

## Структура
```
frontend/
  package.json, pnpm-workspace.yaml, tsconfig.base.json, eslint.config.js
  apps/kiosk/      # киоск: полный экран, камера, QR-скан, челлендж (GUARD через Keycloak + ключ киоска)
  apps/worker/     # PWA рабочего: активация по инвайту, ключ устройства, динамический QR
  apps/admin/      # админка: Keycloak SSO, все разделы
  packages/api/    # типы из contracts/openapi.json (openapi-typescript) + openapi-fetch клиент; генерится, не коммитится
  packages/auth/   # react-oidc-context обёртка (admin, kiosk) + device-auth (worker)
  packages/ui/     # Mantine-тема, общие компоненты: ErrorState, EmptyState, ReasonBanner, CameraView
```
Каждое приложение собирается с `base: '/kiosk/' | '/worker/' | '/admin/'` и отдаётся nginx.

## Правила
- Данные с сервера — только через `packages/api` + TanStack Query. Ручной `fetch` запрещён.
- Решение о проходе не вычисляется на клиенте. Киоск отображает `decision/code/message/hint` из ответа сервера.
- Тексты отказов берём из ответа сервера; `packages/ui/reasons.ts` — только иконки и цвета по `code`.
- Каждый экран: loading / empty / error (кнопка «Повторить») / data + баннер «Нет связи» (`navigator.onLine` + ошибки запросов).
- Токены OIDC держим в памяти (`InMemoryWebStorage`), не в localStorage.
- Ключ устройства: `crypto.subtle.generateKey({name:'ECDSA', namedCurve:'P-256'}, false, ['sign'])`, хранение в IndexedDB (неизвлекаемый).
- Камера: `getUserMedia({video:{facingMode:'user', width:640, height:480}})`, кадры — JPEG 0.85 через canvas. Нужен HTTPS.
- Realtime: `EventSource` с переподключением. Токен передаётся через короткоживущий `?ticket=` (выдаёт `/api/stream/ticket`), не через заголовок.
- Доступность: контраст, крупные кнопки (киоск ≥ 64px, телефон ≥ 48px), всё на русском.

## Команды
```bash
pnpm install
pnpm gen:api                 # из contracts/openapi.json
pnpm --filter admin dev
pnpm -r lint && pnpm -r typecheck && pnpm -r build
```
