# Что фронтенд ждёт от бэкенда («обратный сваггер»)

Статус: черновик к синку по контракту · Источник: моки песочницы `Digital-Hustle/proxodnaya-sandbox`, `src/shared/api/mock/*` (ADR-037…046 песочницы) · Основной контракт — [API.md](API.md).

Здесь перечислено всё, что сейчас замокано в браузере, и как это должно выглядеть на сервере, чтобы фронт переключился с моков без переделки экранов. Для каждой функции мока указан эндпоинт, тело запроса (`*Rq`), ответ (`*Rs`) и ошибки. Если эндпоинт уже есть в API.md — помечено **[есть]**, если его нужно добавить или поменять — **[новый]** / **[изменить]**.

## Общие правила
- Путь через gateway: `/<service>/api/v1/...`, сервисы `people`, `access`, `shift`, `assistant`. Внутренние вызовы — `/internal/**`, наружу не выходят.
- Коллекции — в обёртке со страницей: `{ items: [...], page: { next: string|null, total?: number } }`. Курсор непрозрачный, `limit` ≤ 200 (`PAGE_MAX`). Поиск и фильтры — на сервере (ADR-039): фронт никогда не грузит список целиком.
- Время — Unix ms (`number`) в теле, даты дня — `YYYY-MM-DD` в часовом поясе объекта, время смены — `HH:MM`.
- Ошибки — `application/problem+json`: `{ type, title, status, detail, code, traceId, errors? }`. **`detail` показывается пользователю как есть**, поэтому — по-русски, продуктовым тоном («Код устарел — запросите новый»). `code` — машинный, фронт по нему ветвится.
- Решение о проходе всегда принимает сервер (или терминал по подписанному снимку без связи), UI только показывает ответ. Коды отказов и тексты — таблица в API.md + `FACE_NOT_ENROLLED`, `REPEAT_SCAN`, `OFFLINE_EXPIRED`, `MANUAL_GUARD` (см. ниже).
- Аутентификация: персонал — `Authorization: Bearer <JWT>` после входа по коду из письма (раздел 1); телефон — device-токен ES256; терминал — подпись запроса ключом киоска (`X-Kiosk-Id`, `X-Ts`, `X-Nonce`, `X-Signature`).
- Живые обновления — SSE. Сейчас вкладки синхронизируются через `BroadcastChannel`, в продукте — `GET /shift/api/v1/stream/situation` и `/stream/worker` **[есть]**.

## 1. Вход персонала по коду из письма (ADR-046) **[новый]**
В API.md вход персонала — OIDC Keycloak. Фронт сделан под **беспарольный вход по одноразовому коду**: адрес почты → 6 цифр из письма → сессия. На бэкенде это может быть Keycloak (authenticator «email OTP» в browser/direct-grant flow) или тонкий слой в `people` поверх Keycloak — фронту важны только эндпоинты ниже.

| Мок | Эндпоинт | Rq | Rs |
|---|---|---|---|
| `requestLoginCode(email)` | `POST /people/api/v1/auth/staff/code` | `{ email }` | `{ sentTo: "o.s•••••@proxodnaya.ru", expiresAt, resendAt }` |
| `verifyLoginCode(email, code)` | `POST /people/api/v1/auth/staff/code/verify` | `{ email, code }` | `{ accessToken, refreshToken?, expiresAt, user: StaffUserRs }` |
| `sessionUser(token)` | `GET /people/api/v1/auth/staff/me` | — | `StaffUserRs` или 401 |
| `signOutAdmin(token)` | `POST /people/api/v1/auth/staff/logout` | — | `204` |

- `StaffUserRs = { id, name, email, role: ADMIN|SECURITY_OFFICER|MANAGER|INSTALLER|GUARD, status: ACTIVE|INVITED|DISABLED, lastSeen? }`.
- Код: 6 цифр, живёт 10 мин, хранится только солёным хэшем, один активный на адрес, 5 неверных попыток → код сгорает. Повторная отправка не раньше 45 с (`429`, `code: LOGIN_RESEND_TOO_SOON`, `detail: "Повторно отправить код можно через N с"`).
- **Ответ на `/code` одинаковый для любого адреса** — по нему нельзя узнать, есть ли пользователь. Письмо уходит только активным/приглашённым.
- Ошибки verify: `400 LOGIN_CODE_INVALID` («Неверный код. Осталось попыток: N»), `410 LOGIN_CODE_EXPIRED` («Код устарел — запросите новый»), `429 LOGIN_CODE_LOCKED` («Код введён неверно 5 раз — запросите новый»).
- Сессия 12 ч. Отключённый пользователь (`DISABLED`) теряет доступ сразу: `/me` → 401, фронт показывает «Сессия завершена — войдите снова». Первый вход приглашённого переводит его в `ACTIVE` (событие `JOIN`).
- В журнал доступа пишутся `LOGIN`, `LOGOUT`, `JOIN`.
- Песочница возвращает `demoCode` вместо письма — **в продукте этого поля нет**.

## 2. Доступ и роли (ADR-041) **[новый]**
| Мок | Эндпоинт | Rq → Rs |
|---|---|---|
| `inviteAdmin` | `POST /people/api/v1/staff` (ADMIN) | `{ name, email, role }` → `StaffUserRs` (status `INVITED`, письмо с приглашением) |
| `setAdminRole` | `PATCH /people/api/v1/staff/{id}` | `{ role }` → `StaffUserRs` |
| `setAdminActive` | `POST /people/api/v1/staff/{id}/disable` / `/enable` | — → `StaffUserRs` |
| список | `GET /people/api/v1/staff?q&role&status&cursor` | → `{ items: StaffUserRs[], page }` |
| журнал | `GET /people/api/v1/staff/access-log?cursor` | → `{ items: [{ id, ts, by, target, action: INVITE|ROLE|DISABLE|ENABLE|JOIN|CODE|LOGIN|LOGOUT, from?, to?, code? }], page }` |

Сервер не даёт снять роль ADMIN с последнего администратора или отключить его (`409 LAST_ADMIN`, «Это последний администратор — сначала назначьте другого»).

## 3. Коды на терминалах (ADR-046) **[новый]**
Два кода, общие для всех киосков: **сервисный** (открывает сервисную панель — инженер) и **код охранника** (подтверждает ручной пропуск, в том числе без связи).

| Мок | Эндпоинт | Rq → Rs |
|---|---|---|
| `setTerminalCode(kind, code, by)` | `PUT /access/api/v1/terminal-codes/{kind}` (`kind = service|guard`) | `{ code }` → `{ kind, digits, updatedAt, by }` |
| состояние | `GET /access/api/v1/terminal-codes` | → `{ service?: {digits, updatedAt, by}, guard?: {...} }` (без хэшей) |

- Права: `service` — ADMIN, SECURITY_OFFICER, INSTALLER; `guard` — ADMIN, SECURITY_OFFICER. Иначе `403`, «Недостаточно прав, чтобы менять этот код».
- Стойкость (`422 CODE_WEAK`, `detail` — причина): только цифры, 4–6 знаков, не все одинаковые, не подряд (1234, 9876), не заводские 2580/0000, не похоже на год.
- Хранение: **только PBKDF2-SHA256** `{ salt (16 байт, base64url), hash (32 байта, base64url), iter: 120000, digits }`. Хэш раздаётся терминалам в снимке допусков (`codes`), чтобы код охранника проверялся без связи. Терминал считает ошибки сам: 5 неверных → блокировка ввода на 60 с.
- Пока код не задан, действуют заводские (сервис `2580`, охранник `0000`); админка подсвечивает их как «заводской».
- Смена пишется в журнал доступа (`action: CODE`, `code: service|guard`).

## 4. Телефон сотрудника
| Мок | Эндпоинт | Примечание |
|---|---|---|
| `activateDevice(code)` | `POST /people/api/v1/auth/worker/activate` **[есть]** | Rq `{ inviteCode, deviceId, publicKeyJwk }` → `{ workerId, serverTime, card: { fullName, position, contractor, photoThumb?, sites: [{id,name}] } }` **[изменить: добавить card]** — телефон кэширует карточку для работы без сети |
| `revokeDevice` (выход с телефона) | `DELETE /people/api/v1/worker/me/device` **[новый]** | сотрудник отвязывает свой телефон; вернуться — только по новому коду приглашения |
| история | `GET /access/api/v1/worker/attempts?cursor&limit` **[есть]** | постранично, с `code`, `message`, `checkpoint`, `direction` |
| смены | `GET /shift/api/v1/worker/shifts?from&to` **[есть]** | для календаря |
| статус | `GET /shift/api/v1/worker/me` **[есть]** | `{ inside, zoneId?, since?, shift?: {start,end}, workedMsToday, sites: [{site, zones}] }` |

**QR пропуска** (без запроса к серверу, работает без сети): `v1.<workerId>.<deviceId>.<window>.<sig>`, `window = floor(unix_s / 30)`, `sig = base64url(ECDSA-P256-SHA256("v1|workerId|deviceId|window"))`, ключ неизвлекаемый, в IndexedDB. Один QR действует на всех объектах сотрудника (ADR-044). Допуск окна — `qrToleranceSec`. Каждая пара `deviceId|window` гасится после первого прохода (`QR_REUSED`). *В песочнице в QR дополнительно едет публичный ключ — в продукте его нет, ключ берётся из привязки.*

### Эталон лица (ADR-046) **[новый]**
| Мок | Эндпоинт | Rq → Rs |
|---|---|---|
| `enrollFace` | `POST /people/api/v1/worker/me/face` (device-токен) | `{ at, signature, photo (jpeg base64, ≤ 480px), liveness: { frames: [...] } }` → `{ status: "PENDING" }` |
| `reviewFace` | `POST /people/api/v1/workers/{id}/face/review` (SECURITY_OFFICER, ADMIN) | `{ approve: boolean, comment? }` → `FaceRefRs` |
| при оформлении | `POST /people/api/v1/workers/{id}/face` **[есть]** | снимок HR → эталон сразу `ACTIVE`, `source: HR` |

- `FaceRefRs = { status: NONE|PENDING|ACTIVE|REJECTED, source?: HR|PHONE|KIOSK, at?, by?, comment? }` — отдаётся в карточке сотрудника и в `/worker/me`.
- Подпись: `signature = ECDSA-P256-SHA256("face|workerId|deviceId|at")` ключом телефона; `|now − at| ≤ 5 мин`. Чужой или отвязанный телефон → `403 DEVICE_UNKNOWN`.
- Живость проверяет `face` (`/internal/face/verify`), отказ — `LIVENESS_FAILED` / `FACE_LOW_QUALITY` с понятным `detail`.
- Эталон с телефона **не включается сам**: до подтверждения человеком `status = PENDING`, на терминале «QR + лицо» сотрудник получает `FACE_NOT_ENROLLED` и проходит через охранника.

## 5. Терминал (киоск)
| Мок | Эндпоинт | Rq → Rs |
|---|---|---|
| регистрация киоска | `POST /access/api/v1/kiosk/register` **[новый]** | `{ publicKeyJwk }` → `{ kioskId, pairCode }` (код сопряжения на экране) |
| `pairKiosk(code, …)` | `POST /access/api/v1/kiosks/pair` (ADMIN, INSTALLER) **[изменить]** | `{ pairCode, name, checkpointId, mode?, offlinePolicy? }` → `KioskRs` |
| `updateKiosk` | `PATCH /access/api/v1/kiosks/{id}` | `{ name?, checkpointId?, mode?, offlinePolicy? }` → `KioskRs` |
| `unpairKiosk` | `POST /access/api/v1/kiosks/{id}/unpair` | → `204`, киоск получает новый `pairCode`, ключ и цепочка журнала сохраняются |
| список | `GET /access/api/v1/kiosks` | → `{ items: KioskRs[] }`, `KioskRs = { id, name, checkpointId, mode, offlinePolicy, pairedAt, lastSeen, snapshotAt, chain: {seq, hash}, syncError? }`; «в сети» = `lastSeen` моложе 45 с |
| `kioskScan(qr, checkpointId, mode)` | `POST /access/api/v1/kiosk/attempts` **[изменить]** | `{ qr }` → `ScanRs`. **Без `direction`**: направление выводит сервер (ADR-037) |
| `kioskFrames(token, frames)` | `POST /access/api/v1/kiosk/attempts/{token}/frames` **[есть]** | `{ frames }` → `DecisionRs` |
| `kioskIdentify` («Сначала лицо») | `POST /access/api/v1/kiosk/identify` **[новый]** | `{ frames }` → `DecisionRs` (1:N со строгим порогом `faceThreshold + FACE_FIRST_MARGIN`; не узнали — `FACE_NOT_FOUND`, киоск предлагает QR) |
| `kioskManual` | `POST /access/api/v1/kiosk/manual` **[новый]** | `{ workerId, note, guardCode? }` → `DecisionRs` (`decision: MANUAL`, `code: MANUAL_GUARD`) |
| `reviewManual` | `POST /access/api/v1/attempts/{id}/review` (SECURITY_OFFICER) | `{ status: CONFIRMED|DISPUTED, comment? }` |
| `kioskSnapshot` | `GET /access/api/v1/kiosk/offline-snapshot` **[изменить]** | → `OfflineSnapshotRs` (ниже) |
| `syncOffline(batch, sig)` | `POST /access/api/v1/kiosk/offline-attempts` **[изменить]** | `{ batch, signature }` → `{ ok, error?, accepted: [id], synced, conflicts, rejected }` |

- `ScanRs = { kind: "decision", result: DecisionRs } | { kind: "challenge", token, challenge: { kind: turn-left|turn-right|blink|nod, text, timeoutMs }, worker: { id, fullName, position, contractor, photoThumb? } }`.
- `DecisionRs = { attemptId, decision: ALLOW|DENY|MANUAL|ERROR, code, message, hint, worker?, direction: IN|OUT, score?, ts }`. `message`/`hint` показываются на киоске как есть.
- Логика терминала `mode`: `QR_FACE` (по умолчанию), `FACE_FIRST`, `QR_ONLY`; область — одна для всех (`terminalScope = GLOBAL`) или своя у киоска (`PER_KIOSK`). Без связи (`offlinePolicy`): `GUARD` — только охранник, `CLOSED` — проход закрыт, `LOCAL` — только для `QR_ONLY`, проверка по снимку (ADR-042).
- Незнакомый киоск (не сопряжён) получает на любой запрос `403 KIOSK_NOT_PAIRED` и показывает код сопряжения.

**Снимок допусков** `OfflineSnapshotRs = { at, kioskId, checkpoint, workers: [{id, fullName, position, contractor, status, zoneIds, permitUntil}], devices: [{id, workerId, publicKey, revokedAt?}], shifts, inside: [workerId], rules: { qrToleranceSec, shiftGraceMin, requireShift, repeatScanCooldownSec, maxHours, afterExpiry: GUARD|CLOSED, checkShift, unknownDevice: DENY }, codes?: { service?: TerminalCodeHash, guard?: TerminalCodeHash } }`. Закрытых ключей и фото полного размера в снимке нет. Подписывается ключом сервера (`signature` рядом), терминал проверяет перед использованием.

**Пакет проходов без связи** (ADR-043): `batch = { v: 1, kioskId, nonce, sentAt, events: [{ id, ts, workerId?, checkpointId, direction, decision, code, useKey: "deviceId|window", seq, prev, hash, proof: <исходный QR> }] }`.
- `hash = base64url(SHA-256(canonical(событие без hash)))`, `prev` — хэш предыдущего, первый — `"genesis"`; `canonical` — JSON с ключами по алфавиту, без `undefined`.
- `signature = ECDSA-P256-SHA256(canonical(batch))` ключом терминала (неизвлекаемый, зарегистрирован при `register`).
- Сервер: проверяет подпись пакета, непрерывность цепочки от последнего принятого звена (`kiosk.chain`), перепроверяет подпись сотрудника в `proof`, гасит `useKey`; повтор на другом терминале → конфликт. Принятые `id` возвращаются в `accepted` — только их терминал удаляет из очереди.

## 6. Люди, смены, журнал (кабинет)
| Мок | Эндпоинт | Примечание |
|---|---|---|
| `queryWorkers` | `GET /people/api/v1/workers?q&filter&cursor&limit` **[изменить: курсор]** | `filter = all|inside|blocked`; строка `WorkerRow = Worker & { inside, insideZoneId? }` |
| `createWorker` | `POST /people/api/v1/workers` **[есть]** | `{ fullName, position, contractor, zoneIds, photo? }` → `Worker` + `inviteCode`; с фото эталон лица сразу `ACTIVE` (HR), без — `NONE` |
| `updateWorker` | `PATCH /people/api/v1/workers/{id}` **[есть]** | новое фото от HR = новый эталон |
| `regenerateInvite` | `POST /people/api/v1/workers/{id}/invite` **[есть]** | → `{ inviteCode, link, expiresAt }` |
| блок / разблок | `POST /people/api/v1/workers/{id}/block` **[есть]** | + `/unblock` **[новый]** |
| `revokeDevice` (админ) | `DELETE /people/api/v1/workers/{id}/devices/{deviceId}` **[есть]** | |
| `queryAttempts` / `exportAttempts` | `GET /access/api/v1/attempts?from&to&checkpoint&worker&decision&reason&source&cursor` **[есть]**, `GET …/attempts.csv` **[новый]** | `source = QR|MANUAL|FACE|OFFLINE`, у офлайн — `offline: { kioskId, syncedAt, conflict?, seq, signed }` |
| `queryShiftRows` | `GET /shift/api/v1/shifts/day?day&q&filter&cursor` **[новый]** | план/факт на день, `filter = all|planned|unplanned`: `{ worker, shift?, intervals: [{ start, end? }] }` |
| `upsertShift` / `deleteShift` | `POST|PATCH|DELETE /shift/api/v1/shifts` **[есть]** | |
| `assignSchedule` | `POST /shift/api/v1/shifts/bulk` **[изменить]** | `{ workerIds, from, to, days: [{ weekday: 0..6 (0 = Пн), start, end }] }` → `{ created }`. **Своё время на каждый день недели** (ADR-046); смены в эти дни заменяются; `start < end`, иначе `422`, «Конец смены должен быть позже начала» |
| `updateCheckpoint` | `PATCH /people/api/v1/checkpoints/{id}` | `{ mode: AUTO|IN|OUT }` |
| `updateSettings` | `PUT /access/api/v1/settings` **[есть]** | все поля `Settings` (порог лица, окна, `repeatScanCooldownSec`, `presenceTtlHours`, `terminalMode`, `terminalScope`, `offlinePolicy`, `offlineMaxHours`, `offlineAfterExpiry`, `offlineCheckShift`, `offlineUnknownDevice`) |
| аналитика | `GET /shift/api/v1/analytics/*` **[есть]** | сейчас считается в `mock/derived.ts`: присутствие, интервалы, часы, опоздания, отказы по кодам |

Песочница-only (в продукте нет): `virtualPassQr`, `forgePassQr` (демо-пульт), `seedScale` (+400 человек), `demoFace`.

## 7. Помощник (ADR-046: стриминг) **[изменить]**
`POST /assistant/api/v1/ask` с `Accept: text/event-stream`. Rq `{ question, history: [{ role: user|assistant, text }] }`. Поток событий:
```
event: tool   data: {"name":"on_site_now"}            ← модель вызвала инструмент (фронт показывает шаг «Смотрю, кто на объекте»)
event: delta  data: {"text":"Сейчас на объекте "}       ← кусок текста, фронт дописывает с анимацией
event: done   data: {"text":"…","tools":["on_site_now"],"sources":["…"],"table":{"columns":[…],"rows":[…]},"mode":"llm|rules"}
event: error  data: {"detail":"Модель недоступна"}
```
- Остановка — клиент закрывает соединение (`AbortController`), сервер прекращает генерацию.
- Цифры — только из инструментов: `on_site_now`, `late_today`, `refusals`, `hours_worked`, `worker_today`. Без модели — тот же поток из правил.
- Ключ модели живёт только на сервере. Без `Accept: text/event-stream` — обычный JSON (`done`-объект).

## 8. Коды отказов — добавить в таблицу API.md
| code | Текст на киоске | Подсказка |
|---|---|---|
| REPEAT_SCAN | Проход уже зарегистрирован | Повторно предъявить пропуск можно через 30 секунд |
| OFFLINE_EXPIRED | Автономная проверка недоступна | Связи нет слишком долго — обратитесь к охраннику |
| MANUAL_GUARD | Пропущен охранником | Решение записано в журнал с причиной |
| FACE_NOT_ENROLLED | Лицо ещё не добавлено | Добавьте лицо в приложении или обратитесь к охраннику |
| KIOSK_NOT_PAIRED | Терминал не подключён | Обратитесь к инженеру |
