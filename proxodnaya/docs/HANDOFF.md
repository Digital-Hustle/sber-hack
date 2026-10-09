# HANDOFF — кейс «Проходная» (proxodnaya), бэкенд + фронт

_Для агента с пустой памятью и пустой локалкой. Обновлено после merge #99 (FE-01). Читай целиком, потом — скиллы._

## 0. Установка пользователя (как работать)
- Пользователь — miroslavrov (рус., неформально, нетерпелив): **«бери, не спрашивай, работай дальше»**. Не проси разрешений, доводи каждую задачу до merge сам.
- Цель: **закрыть целиком все задачи кейса** (бэкенд + фронт), чтобы фронт (порт песочницы) работал на реальном API. Почта (коды входа), киоски, офлайн — всё, что нужно для боевого вида.
- В конце каждой задачи — короткий отчёт в чат: что сделано (PR #), как проверить, 3–5 строк «как работает», что осталось.
- Мерж делегирован (`proxodnaya/docs/MEMORY.md §3`, ADR-037): squash через API, затем удалить ветку.

## 1. С чего начать (по порядку)
1. Клонировать репо (токен даст пользователь, **не печатать его в вывод**, не коммитить):
   ```bash
   mkdir -p /data && cd /data
   echo 'T=ghp_...' > /tmp/gh.env && chmod 600 /tmp/gh.env && . /tmp/gh.env
   git clone https://x-access-token:$T@github.com/Digital-Hustle/sber-hack.git
   git clone https://x-access-token:$T@github.com/Digital-Hustle/proxodnaya-sandbox.git   # эталон фронта/контракта (только чтение)
   ```
2. Прочитать **заново** (обязательно, правила строгие):
   - `AGENTS.md` (корень), `proxodnaya/AGENTS.md`, `proxodnaya/rules.md`, `proxodnaya/docs/WORKFLOW.md`;
   - скиллы: `.agents/skills/proxodnaya-dev/SKILL.md`, `.agents/skills/backend-spring/SKILL.md` (+ `references/`), `.agents/skills/frontend-fsd/SKILL.md`, `.agents/skills/springboot-security/SKILL.md`, по необходимости `test-driven-development`, `systematic-debugging`, `verification-before-completion`;
   - документы кейса: `proxodnaya/docs/{TASKS,MEMORY,API,BACKEND-CONTRACT,DECISIONS,SECURITY,ARCHITECTURE}.md`, и у каждого сервиса `proxodnaya/services/<svc>/docs/{TASKS,MEMORY}.md`, фронт — `proxodnaya/frontend/docs/{TASKS,MEMORY,ARCHITECTURE,DESIGN}.md`.
   - **`proxodnaya/docs/BACKEND-CONTRACT.md` — главный список того, что фронту нужно от бэка** (помечено `[есть]`/`[новый]`).

## 2. Окружение локально
- Java 21+ (на сервере 21), Node 22+, Python 3.12+/uv (face). Локально gradle-сборку не гоняем — полный CI на сервере (ниже). Локально быстрые проверки:
  - checkstyle по файлам: скачать `checkstyle-13.0.0-all.jar` (github.com/checkstyle/checkstyle/releases) в `/tmp/cs.jar`, затем
    `cd proxodnaya/services && java -Dbasedir=$PWD -Dcheckstyle.dir=$PWD/config/checkstyle -jar /tmp/cs.jar -c config/checkstyle/checkstyle.xml <файлы>`
  - `node .agents/skills/backend-spring/scripts/be-check.mjs proxodnaya/services`
  - `bash proxodnaya/scripts/guard.sh` (ищет через `git grep` — **проверяет только закоммиченное/добавленное в индекс**; делай `git add` перед прогоном).
  - фронт: `cd proxodnaya/frontend && npm ci && npm run lint && npm run typecheck && npm run build && npm run gen:tokens -- --check && npm run fsd-check`
- Частые грабли checkstyle: строки ≤ 140; MultipleStringLiterals; MagicNumber (в т.ч. в аннотациях — выноси в `static final`); `volatile` запрещён (AtomicReference); ClassFanOutComplexity ≤ 35; JavadocMethod с `@throws`; ReturnCount; ParameterNumber.
- Коммит от имени пользователя:
  `git -c user.name=miroslavrov -c user.email=149431165+miroslavrov@users.noreply.github.com commit ...`
- Push: `git push https://x-access-token:$T@github.com/Digital-Hustle/sber-hack.git <branch>`
- PR через API (gh нет): `curl -H "Authorization: token $T" https://api.github.com/repos/Digital-Hustle/sber-hack/pulls -d @pr.json` (title/head/base=main/body, собирать jq).
- Merge: `PUT /repos/.../pulls/N/merge {"merge_method":"squash"}`, затем `DELETE /repos/.../git/refs/heads/<branch>`.
- Номер PR вписывай в `TASKS.md` (статус `review #N` → после merge `done #N`) и строку в журнал `MEMORY.md §5` модуля.

## 3. CI — на сервере (GitHub Actions не используем)
- Сервер: `root@217.12.40.215`. SSH-ключ пользователь передаёт отдельно. Конфиг `~/.ssh/config`:
  ```
  Host srv
    HostName 217.12.40.215
    User root
    IdentityFile ~/.ssh/srv
    IdentitiesOnly yes
    StrictHostKeyChecking accept-new
    ServerAliveInterval 30
  ```
  (`chmod 600 ~/.ssh/srv`)
- Запуск: `ssh srv "cd /root/ci && (setsid nohup ./ci.sh <branch> > logs/run-<PR>.txt 2>&1 < /dev/null &)"`
- Что делает `/root/ci/ci.sh`: checkout ветки, **merge origin/main**, по изменённым сервисам — jOOQ (БД в контейнере `ci-pg`), `gradlew :svc:check`, be-check, openapi-diff, python (ruff/mypy/pytest для face), `guard.sh`; ставит commit status `server-ci` в GitHub. **Фронт не проверяет** — фронт проверяй локально (см. §2).
- Статус: `ssh srv "pgrep -af 'ci\.sh feat' | grep -v pgrep"` (пусто = закончился); итог: `ssh srv "cat /root/ci/logs/run-<PR>.txt"` (пишется в конце); логи шагов: `/root/ci/logs/<branch_с_подчёркиваниями>-<sha7>/<step>.log`.
- Правила: **никогда два CI одновременно**; не трогать `/root/ci.sh` (чужой прогон intake/documents), `/root/sub-ci`, `/root/stand`, контейнеры `documents-*`, `sub-pg`, `edge-*`; **никогда `pkill -f`** (убивает чужое и собственную shell).
- Стенд proxodnaya на сервере сейчас не поднят (`/root/stand` — общий, документы/субсидии). Деплой — `.github/workflows/proxodnaya-deploy.yml` (образы `ghcr.io/digital-hustle/gate-*`, в т.ч. `gate-frontend`).

## 4. Что сделано (последнее)
- PPL-05 #91 device-токен ES256 (challenge → token, два издателя JWT в gateway).
- ACC-18 #92 настройки прохода `GET|PUT /access/api/v1/settings` (jsonb, кэш 5 с, применяются порог лица, допуск QR, пауза, блокировка, presence, requireShift; `shiftGraceMin`, `terminal*`, `offline*` пока только хранятся).
- PPL-13 #96 вход персонала по коду из письма (ADR-041 вместо TOTP), `/staff` CRUD, роли, LAST_ADMIN, журнал доступа.
- FE-01 #99 фронт: порт песочницы в `proxodnaya/frontend` на реальный API (см. `frontend/docs/MEMORY.md §1–3`):
  `src/shared/api/remote/{http,auth,store,server,devices,assistant,map,reasons}.ts`; demo удалено; QR `v1.<workerId>.<deviceId>.<window>.<sig>`; BrowserRouter; PWA под `/`; SW не кэширует API; `Dockerfile` + `Caddyfile.spa`; `frontend` в `infra/docker-compose.yml` с `build`; guard проверяет токены генератором фронта (`frontend/scripts/gen-tokens.mjs`).

## 5. Что дальше (приоритет — чтобы фронт заработал end-to-end)
Сверяй с `docs/BACKEND-CONTRACT.md` и `src/shared/api/remote/server.ts|devices.ts` (там видно точные пути/тела, которые шлёт фронт).
1. **people PPL-14**: refresh-токен staff в httpOnly-cookie (verify/refresh ставят cookie, `POST /auth/staff/refresh` принимает пустое тело), logout чистит cookie. Без этого перезагрузка кабинета = повторный вход.
2. **people — телефон**: `GET /worker/me` (device: карточка + zones + sites + face), `DELETE /worker/me/device`, `POST /worker/me/face` (подпись `face|workerId|deviceId|at`, photo + liveness.frames ≤ 3) → PENDING, `POST /workers/{id}/face/review`; face в WorkerRs (status/source/pendingPhoto/dupOf); `face/check` c hint/score/duplicate/exceptWorkerId; ActivationRs `card.sites/photoThumb`; workers list `limit 200`.
3. **people — объекты**: PATCH/DELETE sites/zones/checkpoints (+mode, includeDeleted, ошибки NAME_TAKEN, SITE_NOT_EMPTY, ZONE_HAS_CHECKPOINTS/ZONE_NOT_EMPTY), access-log OBJECT/WORKER.
4. **access ACC-12 киоски** (без него киоск не работает): `POST /kiosk/register {publicKeyJwk}` → `{kioskId, pairCode}` (без auth); `GET /kiosk/me` → `{kiosk, checkpoint, settings}`; `GET /kiosks`, `POST /kiosks/pair {code, checkpointId, name}`, `PATCH /kiosks/{id}`, `POST /kiosks/{id}/unpair`; аутентификация `/kiosk/*` подписью вместо GUARD JWT: заголовки `X-Kiosk-Id, X-Ts, X-Nonce, X-Signature` = ECDSA P-256 над `kiosk|<kioskId>|<METHOD>|<path?query>|<ts>|<nonce>|<sha256b64u(body)>`, окно времени + одноразовый nonce; `KIOSK_NOT_PAIRED`. (PPL-07 тогда закрывается этим.)
5. **access**: `POST /kiosk/identify {checkpointId, frames}` (1:N, порог `faceThreshold + 0.1`, `FACE_NOT_FOUND`); `POST /kiosk/manual {workerId, checkpointId, note}`; `POST /attempts/{id}/review`; review/guard/offline в AttemptRs; фильтры attempts `q`, `review`; GUARD может читать attempts; terminal-codes (PBKDF2-хэши `service|guard`, `CODE_WEAK`, журнал CODE в people).
6. **access ACC-15** офлайн: `GET /kiosk/offline-snapshot` (workers, devices с publicKey raw b64u, shifts, inside, rules, codes) и `POST /kiosk/offline-attempts {batch, signature}` (подпись канонического JSON батча ключом киоска, hash-chain `seq/prev/hash`, `MANUAL_GUARD` с `note`, ответ `{ok, accepted[], synced, conflicts, rejected}`).
7. **shift**: `/shifts/bulk` с заменой смен, `/intervals` для персонала; **assistant**: SSE-ответ (`tool/delta/done/error`) + история; ACC-16 `/internal/access/stats`.
8. Остальное из TASKS: AS-03 GigaChat, AS-10 аномалии, PPL-11 матрица безопасности/IDOR, FACE-06/08/09/10, ACC-06/17, FE-W-06 (геолокация heartbeat), FE-A-10 (аудит), вехи `docs/TASKS.md` (M0–M4, PLT-*).
- После каждой бэкенд-задачи проверь, что соответствующий экран фронта работает (пути в `server.ts`); при расхождении правь тот, кто неправ по контракту, и обнови `docs/API.md`.

## 6. Известные ограничения (говорить пользователю)
- Без SMTP-кредов коды входа не уходят; на стенде `STAFF_LOG_CODES=true` пишет код в лог; первый админ — `STAFF_BOOTSTRAP_ADMIN_EMAIL`.
- Пока нет ACC-12/ACC-15/`/worker/me` — киоск висит на «Регистрируем терминал…», телефон показывает карточку из активации.
- Заводского кода охранника нет: ручной пропуск без связи — только после задания кода в кабинете. Сервисный заводской код `2580` — для первого запуска киоска.
- Токен GitHub и SSH-ключ передавались в чатах — **сменить после хакатона**.
