# Контракт API (черновик, финализировать на синке в Т0+0:45)

> Что фронт ждёт от бэкенда по каждому экрану (обратный сваггер, из моков песочницы) — [BACKEND-CONTRACT.md](BACKEND-CONTRACT.md).

Все запросы идут через gateway: `/<service>/api/v1/...` (`people`, `access`, `shift`, `assistant`); gateway переписывает путь в `/api/v1/...` сервиса (паттерн sueta-rnd-back). Ниже префикс сервиса указан в заголовках разделов. Коллекции всегда в обёртке (`{workers: [...], page: {...}}`), request — `*Rq`, response — `*Rs`. Внутренние вызовы между сервисами — `/internal/**`, наружу gateway их не пропускает. Аутентификация: `Authorization: Bearer <JWT>`. Ошибки — `application/problem+json`: `{type, title, status, detail, code, traceId, errors?}` (`detail` — по-русски для человека, `errors` — поля валидации).

## Auth (people: `/people/api/v1/auth/...`)
Персонал: OIDC через Keycloak (`https://auth.<host>/realms/proxodnaya`), backend проверяет JWT по JWKS. Своих паролей для персонала в API нет.
- **[предложение, песочница ADR-046]** беспарольный вход по коду из письма (Keycloak email-OTP или тонкий слой в people): `POST /auth/staff/code` `{email}` → `{sentTo, expiresAt, resendAt}` (ответ одинаковый для любого адреса; 429 `LOGIN_RESEND_TOO_SOON` раньше 45 с); `POST /auth/staff/code/verify` `{email, code}` → `{accessToken, refreshToken?, expiresAt, user}` (6 цифр, 10 мин, 5 попыток: 400 `LOGIN_CODE_INVALID`, 410 `LOGIN_CODE_EXPIRED`, 429 `LOGIN_CODE_LOCKED`); `GET /auth/staff/me`; `POST /auth/staff/logout`. Сессия 12 ч, `DISABLED` → 401 сразу. Подробно — `docs/BACKEND-CONTRACT.md` §1
- `POST /auth/worker/activate` `{inviteCode, deviceId, publicKeyJwk}` → `{workerId, serverTime, card: {fullName, position, contractor, photoThumb?, sites}}` (привязка ключа устройства; этим же ключом телефон подписывает офлайн-QR; `card` — кэш для работы без сети)
- `POST /auth/device/challenge` `{deviceId}` → `{challenge, expiresAt}`
- `POST /auth/device/token` `{deviceId, challenge, signature}` → `{accessToken (ES256, 10 мин)}`
- `POST /auth/kiosk/pair` (GUARD) `{pairingCode, publicKeyJwk}` → `{kioskId, checkpoint}`; далее все `/kiosk/*` подписываются: `X-Kiosk-Id`, `X-Ts`, `X-Nonce`, `X-Signature`
- `GET /shift/api/v1/stream/ticket` → `{ticket}` одноразовый на 30 с для `EventSource`

## Worker (device-токен; профиль — people, смены и поток — shift, история — access)
- **QR без запроса к серверу**: `v1.<deviceId>.<window>.<sig>`, `window = floor(unixTime / 30)`, `sig = base64url(ECDSA-P256-SHA256("v1|deviceId|window"))`. Телефон перерисовывает QR каждые 30 с с таймером; часы сверяет по `serverTime` при онлайне.
- `GET /worker/me` → статус: на объекте, зона, смена, отработано сегодня
- `GET /worker/attempts?limit=` — история с причинами
- `GET /worker/shifts?from&to`
- `POST /worker/heartbeat` `{lat, lon, accuracy}`
- `DELETE /worker/me/device` — сотрудник отвязывает свой телефон («Выйти с этого телефона»)
- `POST /people/api/v1/worker/me/face` `{at, signature, photo, liveness: {frames}}` → `{status: PENDING}`; `signature = ECDSA-P256-SHA256("face|workerId|deviceId|at")` ключом телефона, `|now−at| ≤ 5 мин`. Эталон с телефона включается только после `POST /workers/{id}/face/review` (ADR-046)
- `GET /stream/worker` (SSE): `attempt.result`, `reminder.exit`

## Kiosk (access: `/access/api/v1/kiosk/...`; роль GUARD + подпись ключом киоска)
- `POST /kiosk/attempts` `{qr}` (направление выводит сервер, ADR-037 песочницы; `direction` больше не нужен) → `{attemptId, challenge: {action: TURN_LEFT|TURN_RIGHT|..., framesRequired, expiresAt}}` (если QR невалиден — сразу DENY)
- `POST /kiosk/attempts/{id}/frames` `{frames: [base64 jpeg]}` → `{decision: ALLOW|DENY, code, message, hint, worker?: {name, photoThumb}}`

- `GET /kiosk/offline-snapshot` → `{issuedAt, expiresAt, workers: [{id, name, photoThumb, devices: [{deviceId, publicKeyJwk}], shiftWindow, permitsValidUntil}], codes?: {service?, guard?}, signature}` (ADR-029, только при `OFFLINE_MODE_ENABLED`; `codes` — PBKDF2-хэши кодов терминала `{salt, hash, iter, digits}`, чтобы код охранника проверялся без связи)
- `POST /kiosk/identify` `{frames}` → решение (режим «Сначала лицо», 1:N со строгим порогом); `POST /kiosk/manual` `{workerId, note}` → `MANUAL_GUARD`
- `POST /kiosk/offline-attempts` `{attempts: [{localId, ts, qr, direction, guardDecision, guardReason, prevHash, hash}]}` → `{accepted, conflicts: [...]}`

## Admin / Manager (people: workers/zones/checkpoints/staff; access: attempts/security; shift: shifts/intervals/analytics/reports/stream/alerts; assistant)
- `GET|POST|PATCH /workers`, `POST /workers/{id}/face` (кадры) → `{quality, accepted}`, `POST /workers/{id}/invite` → `{inviteCode, qrPayload, expiresAt}`, `POST /workers/{id}/block`
- `GET|DELETE /workers/{id}/devices`
- `GET|POST /zones`, `GET|POST /checkpoints`, `POST /checkpoints/{id}/pairing-code`
- `GET|POST /shifts`, `POST /shifts/bulk` `{workerIds, from, to, days: [{weekday 0..6 (0=Пн), start, end}]}` (своё время на каждый день недели), `GET|POST /shift-templates`
- `GET /attempts?from&to&checkpoint&worker&decision&reason&source`
- `GET /intervals?from&to&worker&zone`
- `GET /analytics/summary|hours|late-overtime|refusals|load-by-hour|occupancy` (+ общие фильтры)
- `GET /reports/timesheet.xlsx?from&to&contractor`
- `GET /stream/situation` (SSE): `presence.changed`, `attempt.created`, `alert.created`
- `GET /alerts`, `POST /alerts/{id}/ack`
- `POST /assistant/ask` `{question, history?}` → `{text, sources, table?, validation}`; с `Accept: text/event-stream` — поток `tool {name}` / `delta {text}` / `done {…ответ}` / `error {detail}`, остановка — закрытие соединения; `GET /assistant/insights`; `GET /assistant/status`
- `GET|PUT /settings` (порог лица, окна смены, допуск часов QR)
- `GET /security/audit`, `GET /security/login-events`, `POST /security/journal/verify` → `{ok, brokenAt?}` (SECURITY_OFFICER)
- `POST /staff` (ADMIN) — создать сотрудника в Keycloak через provisioner; `PATCH /staff/{id}` `{role}`, `POST /staff/{id}/disable|enable` (409 `LAST_ADMIN`), `GET /staff/access-log`
- `GET /access/api/v1/terminal-codes`, `PUT /access/api/v1/terminal-codes/{service|guard}` `{code}` — коды терминала (4–6 цифр, 422 `CODE_WEAK`; хранится только PBKDF2-SHA256; service — ADMIN/SECURITY_OFFICER/INSTALLER, guard — ADMIN/SECURITY_OFFICER)
- `POST /workers/{id}/face/review` (SECURITY_OFFICER) `{approve, comment?}` — подтвердить эталон лица с телефона

## Коды отказов прохода
| code | Текст на киоске | Подсказка |
|---|---|---|
| QR_MISSING | QR-код не распознан | Покажите QR из приложения |
| QR_INVALID | Недействительный пропуск (формат или подпись) | Откройте приложение «Проходная» |
| QR_EXPIRED | QR устарел или часы телефона сбиты | Подождите новый QR; проверьте время на телефоне |
| QR_REUSED | Этот QR уже использован | Каждый QR одноразовый — дождитесь следующего |
| DEVICE_UNKNOWN | Телефон не привязан | Обратитесь к администратору |
| DEVICE_REVOKED | Телефон отвязан | Обратитесь к администратору |
| DEVICE_MISMATCH | Пропуск с чужого устройства | Обратитесь к администратору |
| WORKER_BLOCKED | Доступ заблокирован | Обратитесь к администратору |
| FACE_NOT_FOUND | Лицо не найдено | Встаньте напротив камеры |
| FACE_MULTIPLE | В кадре несколько человек | Подходите по одному |
| FACE_LOW_QUALITY | Плохое качество кадра | Подойдите ближе, снимите очки/капюшон |
| FACE_MISMATCH | Лицо не совпадает с владельцем пропуска | Проход только по своему пропуску |
| LIVENESS_FAILED | Проверка не пройдена | Выполните действие на экране |
| CHALLENGE_EXPIRED | Время вышло | Начните заново |
| NO_SHIFT | Нет смены на сегодня | Обратитесь к прорабу |
| OUTSIDE_SHIFT_WINDOW | Вне времени смены | Смена с HH:MM |
| PERMIT_EXPIRED | Просрочен инструктаж/медосмотр | Обратитесь к ответственному по ТБ |
| NO_ZONE_PERMIT | Нет допуска в эту зону | — |
| ALREADY_INSIDE | Вы уже на объекте | Сначала отметьте выход |
| NOT_INSIDE | Вход не отмечен | Отметьте вход |
| ZONE_FULL | Зона переполнена | Подождите |
| TEMP_LOCKED | Слишком много неудачных попыток | Обратитесь к охране |
| KIOSK_SIGNATURE_INVALID | Киоск не авторизован | Обратитесь к администратору |
| SYSTEM_ERROR | Система недоступна, проход закрыт | Обратитесь к охране |
| REPEAT_SCAN | Проход уже зарегистрирован | Повторно предъявить пропуск можно через 30 секунд |
| OFFLINE_EXPIRED | Автономная проверка недоступна | Связи нет слишком долго — обратитесь к охраннику |
| MANUAL_GUARD | Пропущен охранником | Решение записано в журнал с причиной |
| FACE_NOT_ENROLLED | Лицо ещё не добавлено | Добавьте лицо в приложении или обратитесь к охраннику |
| KIOSK_NOT_PAIRED | Терминал не подключён | Обратитесь к инженеру |

## Внутренние API (только service accounts)
- face: `POST /internal/face/verify` `{workerId, frames[], challenge}` → `{match, score, liveness, quality, reason?}`
- people: `GET /internal/people/workers/{id}/access-profile` → `{status, deviceIds, permits[], zoneIds}`
- shift: `GET /internal/shift/workers/{id}/active-shift?at=` → `{shiftId, start, end, windowOk}`
- access: `GET /internal/access/stats?...` (для assistant)
