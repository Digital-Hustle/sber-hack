# Контракт API (черновик, финализировать на синке в Т0+0:45)

Все запросы идут через gateway: `/api/<service>/...` (`people`, `access`, `shift`, `assistant`, `notify`). Ниже префикс сервиса указан в заголовках разделов. Внутренние вызовы между сервисами — `/internal/**`, наружу gateway их не пропускает. Аутентификация: `Authorization: Bearer <JWT>`. Ошибки — `application/problem+json`: `{type, title, status, code, message, details?}`.

## Auth (people-service: `/api/people/auth/...`)
Персонал: OIDC через Keycloak (`https://auth.<host>/realms/gate`), backend проверяет JWT по JWKS. Своих логинов для персонала в API нет.
- `POST /auth/worker/activate` `{inviteCode, deviceId, publicKeyJwk}` → `{workerId}` (привязка ключа устройства)
- `POST /auth/device/challenge` `{deviceId}` → `{challenge, expiresAt}`
- `POST /auth/device/token` `{deviceId, challenge, signature}` → `{accessToken (ES256, 10 мин)}`
- `POST /auth/kiosk/pair` (GUARD) `{pairingCode, publicKeyJwk}` → `{kioskId, checkpoint}`; далее все `/kiosk/*` подписываются: `X-Kiosk-Id`, `X-Ts`, `X-Nonce`, `X-Signature`
- `GET /stream/ticket` → `{ticket}` одноразовый на 30 с для `EventSource`

## Worker (роль WORKER; токен — access, профиль и смены — people/shift, поток — notify)
- `POST /worker/pass-token` → `{token, expiresAt, ttlSeconds}`
- `GET /worker/me` → статус: на объекте, зона, смена, отработано сегодня
- `GET /worker/attempts?limit=` — история с причинами
- `GET /worker/shifts?from&to`
- `POST /worker/heartbeat` `{lat, lon, accuracy}`
- `GET /stream/worker` (SSE): `attempt.result`, `reminder.exit`

## Kiosk (access-service: `/api/access/kiosk/...`; роль GUARD + подпись ключом киоска)
- `POST /kiosk/attempts` `{token, direction: ENTRY|EXIT}` → `{attemptId, challenge: {action: TURN_LEFT|TURN_RIGHT|..., framesRequired, expiresAt}}` (если токен уже невалиден — сразу DENY)
- `POST /kiosk/attempts/{id}/frames` `{frames: [base64 jpeg]}` → `{decision: ALLOW|DENY, code, message, hint, worker?: {name, photoThumb}}`

## Admin / Manager (people: workers/zones/checkpoints/staff; access: attempts/security; shift: shifts/intervals/analytics/reports; assistant: assistant; notify: stream/alerts)
- `GET|POST|PATCH /workers`, `POST /workers/{id}/face` (кадры) → `{quality, accepted}`, `POST /workers/{id}/invite` → `{inviteCode, qrPayload, expiresAt}`, `POST /workers/{id}/block`
- `GET|DELETE /workers/{id}/devices`
- `GET|POST /zones`, `GET|POST /checkpoints`, `POST /checkpoints/{id}/pairing-code`
- `GET|POST /shifts`, `POST /shifts/bulk`, `GET|POST /shift-templates`
- `GET /attempts?from&to&checkpoint&worker&decision&reason&source`
- `GET /intervals?from&to&worker&zone`
- `GET /analytics/summary|hours|late-overtime|refusals|load-by-hour|occupancy` (+ общие фильтры)
- `GET /reports/timesheet.xlsx?from&to&contractor`
- `GET /stream/situation` (SSE): `presence.changed`, `attempt.created`, `alert.created`
- `GET /alerts`, `POST /alerts/{id}/ack`
- `POST /assistant/ask` `{question}` → `{text, sources, table?, validation}`; `GET /assistant/insights`; `GET /assistant/status`
- `GET|PUT /settings` (порог лица, окна смены, TTL токена)
- `GET /security/audit`, `GET /security/login-events`, `POST /security/journal/verify` → `{ok, brokenAt?}` (SECURITY_OFFICER)
- `POST /staff` (ADMIN) — создать сотрудника в Keycloak через provisioner

## Коды отказов прохода
| code | Текст на киоске | Подсказка |
|---|---|---|
| TOKEN_MISSING | QR-код не распознан | Покажите QR из приложения |
| TOKEN_INVALID | Недействительный пропуск | Откройте приложение и обновите QR |
| TOKEN_EXPIRED | Срок QR истёк | Обновите QR в телефоне |
| TOKEN_REUSED | Этот QR уже использован | Каждый QR одноразовый — обновите |
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

## Внутренние API (только service accounts)
- face: `POST /internal/face/verify` `{workerId, frames[], challenge}` → `{match, score, liveness, quality, reason?}`
- people: `GET /internal/people/workers/{id}/access-profile` → `{status, deviceIds, permits[], zoneIds}`
- shift: `GET /internal/shift/workers/{id}/active-shift?at=` → `{shiftId, start, end, windowOk}`
- access: `GET /internal/access/stats?...` (для assistant)
