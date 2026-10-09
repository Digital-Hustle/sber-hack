# TASKS — frontend

Формат и правила — `../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняешь в своей ветке вместе с кодом; чужие строки не трогаешь. Новая задача — строкой в конец своей секции, ID не переиспользуем. Время — от Т0 (старт хакатона, PLAN.md).

## Порт песочницы (FE-01)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-01 | FR-50..54, FR-60..64 | Порт `proxodnaya-sandbox` как боевого фронта: мок → `shared/api/remote` (gateway, staff/device/kiosk-авторизация, кэш Db + SSE), без демо (пульт, сиды, демо-коды, ключи LLM в браузере), BrowserRouter, Dockerfile, ESLint | F2 | review #99 |

Строки ниже со статусом `review FE-01` закрываются этим портом (реализация песочницы, а не исходный план с axios/TanStack/OIDC).

## Каркас и shared (F2) — первые 45 минут, остальные ждут FE-S-01..03
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-S-01 | FR-53 | Vite + React 19 + TS strict + Tailwind 4 + shadcn в эту папку (не удалять docs/), ESLint/Prettier, husky, скрипты `gen:api`, `gen:tokens`, `typecheck` | F2 | review #99 (FE-01) |
| FE-S-02 | FR-53 | `shared/config/tokens.ts` + `motion.ts` из скилла, `npm run gen:tokens` → `app/styles/theme.css`, `MotionProvider`, шрифты SB Sans | F2 | review #99 (FE-01) |
| FE-S-03 | FR-61 | `shared/api/$api` (axios, refresh, problem+json), `tokenStorage` в памяти, `oidc-client-ts`, `RequireAuth`/`RequireRole`, роутер зон, `AppRoutes` | F2 | review #99 (FE-01) |
| FE-S-04 | FR-54 | `shared/ui`: Button, Input, Card, Badge, Dialog/Sheet, Select/Combobox, Tabs, Table, Toast, Skeleton, Empty/Error/Offline — по DESIGN.md §5 | F2 | review #99 (FE-01) |
| FE-S-05 | B-02 | `shared/api/sse.ts` + `useEventStream` (ticket, backoff, Last-Event-ID) | F2 | review #99 (FE-01) |
| FE-S-06 | FR-54 | `useOnline` + баннер «Нет связи» в layout каждой зоны, `ErrorBoundary` на зону | F2 | review #99 (FE-01) |
| FE-S-07 | — | entities: Session, Worker, Site, Shift, PassAttempt, PassDecision (`reasonView` по кодам API.md), Device | F2 | review #99 (FE-01) |

## Киоск (F1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-K-01 | FR-60/64 | Вход охранника (SSO), сопряжение киоска кодом, ключ киоска, подпись запросов `X-Kiosk-*` (`shared/lib/kioskSign`) | F1 | review #99 (FE-01) |
| FE-K-02 | FR-50 | `KioskScanner`: камера, авто-скан QR (`@zxing/browser`), переключатель вход/выход | F1 | review #99 (FE-01) |
| FE-K-03 | FR-01/12 | `scan-pass` → `POST /kiosk/attempts`; DENY сразу → `DecisionScreen` | F1 | review #99 (FE-01) |
| FE-K-04 | FR-06 | `capture-face` + `ChallengeHint`: подсказка, 3–5 кадров в окне, `POST …/frames` | F1 | review #99 (FE-01) |
| FE-K-05 | FR-50/12 | `DecisionScreen`: цвет/иконка по `code`, текст и подсказка с бэка, звук, автосброс | F1 | review #99 (FE-01) |
| FE-K-06 | FR-14 | `confirm-manual-pass`: ручной проход охранника с причиной («сдвиньте, чтобы подтвердить») | F1 | review #99 (FE-01) |
| FE-K-07 | FR-70 | Офлайн-режим за флагом: снимок, проверка подписи QR, решение охранника, IndexedDB + hash-chain, досылка | F1 | review #99 (FE-01) |

## Телефон рабочего, PWA (F1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-W-01 | FR-63 | `activate-device`: инвайт → ключ ECDSA P-256 (неизвлекаемый, IndexedDB) → `/auth/worker/activate` | F1 | review #99 (FE-01) |
| FE-W-02 | FR-02 | `show-pass-qr` + `PassQR`: подпись `v1|deviceId|window` каждые 30 с, кольцо-таймер, работает без сети | F1 | review #99 (FE-01) |
| FE-W-03 | FR-51 | Статус (на объекте, зона, смена, отработано), device-токен (challenge → ES256) | F1 | review #99 (FE-01) |
| FE-W-04 | FR-51 | История проходов с причинами, график смен | F1 | review #99 (FE-01) |
| FE-W-05 | NFR-20 | PWA: `vite-plugin-pwa` `scope: /worker/`, precache, кэш шрифтов, установка на iOS/Android | F1 | review #99 (FE-01) |
| FE-W-06 | B-01/B-04 | Heartbeat с геолокацией, баннер «отметьте выход» | F1 | todo |

## Админка (F2)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-A-01 | FR-52 | Layout: `AdminSidebar` по ролям из routeConfig, переключатель темы | F2 | review #99 (FE-01) |
| FE-A-02 | FR-13 | Заведение человека ≤ 2 мин: `create-worker` → `upload-face` (вебкамера, quality) → инвайт-QR → смена на сегодня | F2 | review #99 (FE-01) |
| FE-A-03 | FR-52 | Люди: список (курсоры, фильтры в URL), карточка, блок, устройства (`revoke-device`) | F2 | review #99 (FE-01) |
| FE-A-04 | FR-22 | Зоны и проходные, код сопряжения киоска | F2 | review #99 (FE-01) |
| FE-A-05 | FR-20 | Смены и шаблоны, массовое назначение | F2 | review #99 (FE-01) |
| FE-A-06 | FR-10/31 | Журнал проходов `PassJournalTable`: фильтры в URL, миниатюры (ADMIN), причины | F2 | review #99 (FE-01) |
| FE-A-07 | FR-30/32 | Дашборд «сейчас на объекте» (`OnSiteNow`), KPI с «как посчитано», неполные данные отдельно | F2 | review #99 (FE-01) |
| FE-A-08 | FR-24/33 | Интервалы и отчёты, выгрузка табеля XLSX (`export-timesheet`) | F2 | review #99 (FE-01) |
| FE-A-09 | FR-42/44 | `AssistantChat`: вопрос → текст + источники + таблица + статус валидатора | F2 | review #99 (FE-01) |
| FE-A-10 | FR-68 | «Безопасность»: аудит, входы, «Проверить целостность журнала» | F2 | todo |
| FE-A-11 | B-02/05/07/08 | Realtime-дашборд по SSE, алерты, графики отказов и нагрузки | F2 | review #99 (FE-01) |
| FE-A-12 | — | Настройки порогов (`GET|PUT /settings`) | F2 | review #99 (FE-01) |
