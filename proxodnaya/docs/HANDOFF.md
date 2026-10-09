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

## 7. ДЕПЛОЙ — твоя первая задача (боевой стенд, живые данные)
**Цель:** полноценный боевой деплой на **https://proxodnaya.digital-hustle.ru** (DNS уже указывает на 217.12.40.215). Никаких демо-кнопок/автозаполнения/сидов — данные вводятся руками через UI. Модель — **по API, не локальная** (сервер загружен другими кейсами, профиль `llm-local` не включать).

**Что уже подготовлено (ветка `feat/DEPLOY-01-stand`, запушена, НЕ смержена — проверь, доведи до merge через серверный CI):**
- `infra/Caddyfile`: адрес `{$CADDY_SCHEME}{$PUBLIC_HOST}` + `trusted_proxies static private_ranges` (работа за общим edge).
- `infra/docker-compose.yml`: caddy `ports: ["${HTTP_BIND:-80}:80", "${HTTPS_BIND:-443}:443"]`, env `CADDY_SCHEME`; `frontend` собирается из `../frontend` (`build:`), без profile.
- `infra/.env.example`: CADDY_SCHEME/HTTP_BIND/HTTPS_BIND.
- фронт: убраны последние демо-тексты (подвал главной, подсказка ассистента «Что с Ивановым»).
- `scripts/deploy-server.sh`: jar-ы (jOOQ из временного postgres `gate-build-pg` на 127.0.0.1:5439, чтобы не мешать `ci-pg:5432`) → `docker compose -p proxodnaya build` → `up -d --wait`. Порт 5434 занят чужим — не использовать.

**Состояние сервера:**
- Общий edge (`/opt/sber-hack/edge`, контейнер `edge-edge-1`, network_mode host) уже держит 80/443 и маршрутизирует `proxodnaya.digital-hustle.ru` → `127.0.0.1:8380`. Edge не трогать.
- Рядом работает стенд **documents** (`/opt/sber-hack/documents`, `docker compose ls`) — **не останавливать** и не трогать `/opt/sber-hack`.
- Для «Проходной» склонирован отдельный checkout **`/opt/gate`** (ветка `feat/DEPLOY-01-stand`, detached). Создан `/opt/gate/proxodnaya/infra/.env` (gen-env.sh) с правками:
  `PUBLIC_HOST=proxodnaya.digital-hustle.ru`, `CADDY_SCHEME=http://`, `HTTP_BIND=127.0.0.1:8380`, `HTTPS_BIND=127.0.0.1:8743`,
  `LLM_PROVIDER=openai`, `LLM_BASE_URL=https://api.reformboss.com/v1`, `LLM_MODEL=deepseek-v4-flash`, `LLM_API_KEY=<уже вписан, ключ даст пользователь>`, `LLM_TIMEOUT_SECONDS=60`,
  `SIMULATOR_ENABLED=false`, `STAFF_BOOTSTRAP_ADMIN_EMAIL=admin@digital-hustle.ru` (уточни у пользователя реальную почту!), `STAFF_LOG_CODES=true` (пока нет SMTP — код входа в `docker logs proxodnaya-people-1`), `SMTP_FROM=no-reply@digital-hustle.ru`.
  `.env` не коммитить, не печатать целиком.
- Первый запуск `deploy-server.sh` был прерван (стенд НЕ поднят). Перед повтором: `docker rm -f gate-build-pg`, убедиться, что CI не идёт.
- Диск: ~15 ГБ свободно (из 40), RAM 19 ГБ (≈13 доступно). После сборки — `docker image prune -f`; build-cache чистить `docker builder prune -f` (без `-a` для чужого).

**API-модель (проверено с сервера, из песочницы агента провайдер отвечает «временно недоступен»):** `https://api.reformboss.com/v1`, OpenAI-совместимый, tool calling работает у `deepseek-v4-flash` (≈1,7 с, по умолчанию), `qwen3.8-flash`, `kimi-k2.6`. Список моделей: glm-5.3, glm-5.3-flash, glm-5.3-prime, deepseek-v4.1-flash, deepseek-v4-pro, deepseek-v4-flash, kimi-k3, kimi-k2.7-code, qwen3.8-max, qwen3.8-flash, qwen3.7-max, qwen3.7-plus, grok-4.7, kimi-k2.6 и др. Ассистент уже умеет и локально (ollama), и по API — переключение только переменными `LLM_*` (docs/LLM.md). Требование пользователя: оба режима должны работать.

**Шаги:**
1. Смержить `feat/DEPLOY-01-stand` (серверный CI → squash).
2. `ssh srv 'cd /opt/gate && git fetch -q origin && git checkout -q -f origin/main && (setsid nohup proxodnaya/scripts/deploy-server.sh > /opt/gate/deploy.log 2>&1 < /dev/null &)'`, следить `tail -f /opt/gate/deploy.log` (gradle ~5–10 мин, образы ещё ~5–10).
3. `proxodnaya/scripts/smoke.sh https://proxodnaya.digital-hustle.ru`; открыть `/admin` → вход по коду (код из `docker logs proxodnaya-people-1 | grep -i код`), завести объект/зону/проходную/сотрудника руками.
4. Если Keycloak/ссылки уходят на http — проверить `X-Forwarded-Proto` от edge и `KC_PROXY_HEADERS`/`KC_HOSTNAME` в compose.
5. Описать в `docs/DEPLOY.md` раздел «Стенд за edge» (переменные выше, deploy-server.sh), обновить MEMORY.
6. SMTP: попросить у пользователя креды (`SMTP_HOST/USER/PASSWORD`) → выключить `STAFF_LOG_CODES`.
7. Дальше — задачи §5 по порядку, после каждого merge передеплой (`deploy-server.sh`) и проверка на домене.
