# HANDOFF — кейс «Документы» (для следующего агента)

Ты продолжаешь работу одного агента, который в одиночку делает кейс `documents/` целиком (бэк + фронт + eval + docs).
Дизайн/анимации/стили/логика — как в `Digital-Hustle/proxodnaya-sandbox`. **`subsidy/` и `.agents/skills/*` не трогать** (там работает другой агент; скиллы общие).
Итерация: ветка → коммиты → PR → зелёный CI → **мержишь сам** (разрешено, `docs/MEMORY.md §3`). Этот файл удали в последнем PR.

## 1. Состояние на момент передачи
- Смержены: #12 платформа, #17 intake+ocr, #19 docproc, **#22 review** (рабочее место оператора, порт 8083).
- Ветка **`feat/FE-01-frontend`** (PR ещё не открыт): весь фронт `documents/frontend` — кабинет `/apply` и оператор `/operator`.
  Локально проходили: `tsc`, `eslint --max-warnings 0`, `fsd-check` (0 нарушений), `vite build`, скриншоты.
- **Известный FAIL guard:** «токены/ключи в localStorage» — из‑за `src/shared/ui/ThemeSwitcher.tsx` (тема `documents.theme` в localStorage).
  Посмотри регулярку в `documents/scripts/guard.sh` и исправь честно (например, тему хранить иначе/переименовать так, чтобы правило про токены не ловило тему — если правило ловит само слово localStorage, переведи тему на sessionStorage).
- Строки guard с `Stages.java`/`DocRow.java` и т. п. — это не ошибки, а вывод совпадений по слову FAILED; ошибки — только строки `✖ FAIL`.

## 2. Что осталось (по порядку)
1. Починить guard (п. 1), прогнать `scripts/smoke-ui.mjs` (ниже), поправить, если упадёт.
2. `frontend/Dockerfile`: compose собирает `build: { context: .., dockerfile: frontend/Dockerfile }` (context = `documents/`).
   Стадия `node:22`: COPY `contracts/` и `frontend/` → `npm ci` → `npm run gen:api` → `npm run build`; финал `caddy:2` + `COPY --from=build /app/frontend/dist /srv`.
   Caddyfile монтируется томом (`infra/caddy/Caddyfile`), CSP `script-src 'self'` — без inline-скриптов.
3. `npm audit --audit-level=high` (trivy в CI падает на HIGH/CRITICAL в lock-файле).
4. Документы фронта: `frontend/docs/TASKS.md` (статусы FE-*), `frontend/docs/MEMORY.md` §1–3 (решения ниже), строка про зависимости oidc-client-ts (Apache-2.0) и @tanstack/react-query (MIT) в `docs/DECISIONS.md`.
5. PR → CI → merge.
6. `documents/eval`: `run.py` + генератор синтетики + pytest + ruff, uv (`uv sync --frozen` ⇒ нужен `uv.lock`). CI: `uv run ruff check .`, `uv run pytest -q`. Без подгонки (`rules.md §1`).
7. Docs кейса: README, `docs/DEMO.md` (сценарий соискатель → оператор; логины Keycloak operator/senior/auditor — `infra/keycloak/realm-documents.json`), `LLM.md`, `EVAL.md`, `TASKS.md`/`MEMORY.md`.
8. Финальный отчёт пользователю: PR-ы, как запустить/проверить, риски (DP-14 не сделан; SSE-хабы держат подписки в памяти процесса — один инстанс; PDF-выгрузки нет, только DOCX/XLSX); совет отозвать GitHub-токен.

## 3. Решения фронта (записать в frontend/docs/MEMORY.md §3)
- fetch-обёртка `shared/api/http.ts` вместо axios; ошибки problem+json → `ApiError {status, code, message=detail, hint}`.
- OIDC (oidc-client-ts, PKCE, client `documents-operator`, realm `documents`, redirect `/operator/callback`): токены в `InMemoryWebStorage` — после перезагрузки повторный вход (SSO Keycloak пускает без пароля). Silent-renew через iframe невозможен (CSP `frame-ancestors 'none'`), обновление — refresh_token.
- Ключ заявки: из `#k=` → память + sessionStorage, сразу стирается из адресной строки. Заголовок `Authorization: Applicant <key>`.
- Камера — `<input capture="environment">` (нативная камера), сжатие снимков до 4 МП (`shared/lib/image.ts`).
- Страницы документа intake отдаёт PNG (`/intake/api/v1/files/{id}/content?page=`) → pdfjs не нужен; blob + `revokeObjectURL`.
- bbox поля `[x0,y0,x1,y1]` в координатах `DocumentCard.pages[{n,width,height}]` → проценты поверх `<img>`.
- Токены генерирует **генератор скилла** (`npm run gen:tokens`; guard проверяет `--check`). Генератор sandbox новее, но guard сверяет со скилловым,
  поэтому недостающие утилиты sandbox (`bg-brand-deep`, `bg-sheen`, `bg-glow`, `bg-brand-conic`, `scrollbar-none`, `pb-nav`, `max-h-sheet`, `--tracking-*`) дописаны руками в `src/app/styles/index.css`. `theme.css` руками не править.

## 4. Карта фронта (FSD)
- `shared`: `api/{http,problem,sse,index}.ts` (`useEventStream`: билет → EventSource `?ticket=` → backoff), `lib/{applicationKey,image,…}`, `config/{tokens,motion,env}.ts`, `const/router.ts`, `ui/*` (из sandbox; Logo → «Документы», добавлен `Textarea`).
- `entities`: `session` (OIDC, роли из `realm_access.roles`, `can()`), `document` (`stageView`, `reasonView`, `checkLabel`), `application` (`applicantApi`, `reviewApi`, ключи запросов, `statusView`…).
- `features`: start-application, fill-profile, upload-document, submit-application, take-next, review-document, decide-application, request-resubmit, export-documents.
- `widgets`: apply-shell, document-checklist, operator-shell (+ModelStatusBadge), document-viewer, fields-panel (J/K/E, «Показать» номер), history-timeline.
- `pages`: home, apply (Start, Application), operator (Queue, Application, Expiring, Metrics, Callback), not-found. Роутер — `app/providers/router.tsx` (BrowserRouter, зоны — ленивые чанки).
- Типы API: `npm run gen:api` → `src/shared/api/schema/` (в .gitignore; CI генерирует сам).
- `fsd-check` запрещает: `w-[..]`/`aspect-[..]`, hex/rgb, числа в duration/ease (только пресеты `@/shared/config/motion`), `@keyframes` вне `shared/ui`, импорты вверх и мимо `index.ts`.

## 5. Окружение с нуля
```bash
git clone https://github.com/Digital-Hustle/sber-hack && cd sber-hack && git checkout feat/FE-01-frontend
cd documents/frontend && npm ci && npm run gen:api && npm run gen:tokens -- --check && npm run lint && npm run typecheck && npm run build && npm run fsd-check
cd ../.. && git add -A documents && sh documents/scripts/guard.sh    # guard смотрит индекс git — сначала git add
```
- Java-сервисы: JDK 21, `cd documents/services && ./gradlew :review:check :review:exportOpenApi` (интеграционные тесты хотят PostgreSQL 16 на localhost, postgres/postgres, БД intake/docproc/review). Сборка 4–5 мин — запускай в фоне (`nohup … &`), ход агента ограничен по времени; большие пачки файлов пиши частями.
- `node .agents/skills/backend-spring/scripts/be-check.mjs documents/services` — правила бэка (сейчас 0 нарушений).
- Checkstyle Java: строка ≤ 140, перевод строки в конце файла, без static import (кроме тестов), пустые блоки только с комментарием, абстрактные классы `Abstract*`, javadoc — первая фраза с заглавной, вторая строка javadoc не должна начинаться со строчной кириллицы.
- gitleaks сканирует всю историю — тестовые секреты генерируй в рантайме, токены не коммить.
- CI: `.github/workflows/documents-ci.yml` (frontend: node 22, npm ci → gen:api → gen:tokens --check → lint → typecheck → build → fsd-check; security: gitleaks + guard + trivy; eval; infra).

## 6. GitHub без gh CLI (токен — от пользователя, в переменной окружения `GH_TOKEN`, не в файлах репо)
```bash
cat > ~/gh.sh <<'SH'
#!/usr/bin/env bash
# gh.sh pr <branch> <title> <bodyfile> | checks <sha> | wait <sha> | jobs <sha> | log <jobId> [n] | merge <n> <title>
set -euo pipefail
R=https://api.github.com/repos/Digital-Hustle/sber-hack
H=(-H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json")
case "$1" in
  pr) jq -n --arg t "$3" --arg h "$2" --rawfile b "$4" '{title:$t,head:$h,base:"main",body:$b}' | curl -s "${H[@]}" -X POST $R/pulls -d @- | jq -r '.number // .';;
  checks) curl -s "${H[@]}" "$R/commits/$2/check-runs?per_page=100" | jq -r '.check_runs[] | "\(.status) \(.conclusion) \(.name)"';;
  wait) for i in $(seq 1 90); do out=$(curl -s "${H[@]}" "$R/commits/$2/check-runs?per_page=100"); n=$(jq '.total_count' <<<"$out"); p=$(jq '[.check_runs[]|select(.status!="completed")]|length' <<<"$out"); if [ "$n" -gt 0 ] && [ "$p" -eq 0 ]; then jq -r '.check_runs[] | "\(.conclusion) \(.name)"' <<<"$out"; exit 0; fi; sleep 20; done; echo TIMEOUT;;
  jobs) curl -s "${H[@]}" "$R/commits/$2/check-runs?per_page=100" | jq -r '.check_runs[] | select(.conclusion=="failure") | "\(.id) \(.name)"';;
  log) curl -s "${H[@]}" "$R/actions/jobs/$2/logs" -L | tail -${3:-60};;
  merge) jq -n --arg t "$3" '{merge_method:"squash",commit_title:$t}' | curl -s "${H[@]}" -X PUT $R/pulls/$2/merge -d @- | jq -r '.message';;
esac
SH
chmod +x ~/gh.sh
git remote set-url origin "https://x-access-token:${GH_TOKEN}@github.com/Digital-Hustle/sber-hack.git"   # пуш по токену
```
- Всё зелёное ⇔ `~/gh.sh checks <sha> | grep -v "completed success\|skipped"` пусто.
- Мерж — squash: после мержа **не ребейзь** ветку на main (конфликты с собственным squash-коммитом), а создавай новую ветку от `origin/main`.

## 7. Ручной смоук UI
`cd documents/frontend && npm run build && npx vite preview --port 4173 &` → `CHROME=$(which chromium) node scripts/smoke-ui.mjs` → `/tmp/e2e-*.png`
(кабинет `/apply/:id` 390×844, очередь, карточка с bbox, история, метрики, истекающие; вход Keycloak и ответы API подставляет сам скрипт; печатает ошибки страницы).
