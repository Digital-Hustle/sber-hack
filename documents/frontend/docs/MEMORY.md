# MEMORY — frontend

Память модуля между сессиями и агентами (по мотивам Cline Memory Bank). Правила:
- §1 — **перезаписываемое** текущее состояние (≤ 10 строк, правит владелец/агент задачи в конце сессии).
- §2 — карта: обновляй, когда добавил слайс/важный файл.
- §3–4 — решения и баги: добавляй строку, неактуальное помечай ~~зачёркнутым~~, не удаляй.
- §5 — журнал: **только дописывать в конец** (`.gitattributes: merge=union`).
Архитектурное решение для всей системы — ADR в `../../docs/DECISIONS.md`, здесь только ссылка.

## 1. Текущее состояние
- Фронт готов: кабинет соискателя `/apply` (390×844) и рабочее место оператора `/operator` (очередь, карточка с bbox, история, истекающие, метрики, DOCX/XLSX). PR feat/FE-01-frontend.
- Проверки: tsc, eslint --max-warnings 0, fsd-check (0 нарушений), vite build, guard, `npm audit --audit-level=high` — чисто. Ручной прогон: `scripts/smoke-ui.mjs`.
- Сборка образа: `frontend/Dockerfile` (node:22 → caddy:2), контекст `documents/`, Caddyfile томом из `infra/caddy/`.
- Не сделано: Last-Event-ID при переподключении SSE (вместо него перезапрос данных), husky/prettier, выгрузка PDF (только DOCX/XLSX).

## 2. Карта модуля
| Путь | Что | Владелец |
|---|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, DESIGN, TASKS, MEMORY | агент |
| `src/app/` | `main.tsx`, `providers/router.tsx` (BrowserRouter, зоны — ленивые чанки), `styles/index.css` (ручные утилиты из sandbox) | агент |
| `src/app/styles/theme.css` | генерируется `gen:tokens`, не править | — |
| `src/shared/api/` | `http.ts` (fetch + problem+json → `ApiError`), `problem.ts`, `sse.ts` (`useEventStream`), `schema/` (генерируется, в .gitignore) | агент |
| `src/shared/{lib,config,const,ui}` | `applicationKey`, `image` (сжатие до 4 МП), `tokens`/`motion`/`env`, `router`, компоненты из sandbox + `Textarea` | агент |
| `src/entities/` | `session` (OIDC, роли, `can()`), `document` (`stageView`, `reasonView`, `checkLabel`), `application` (`applicantApi`, `reviewApi`, ключи запросов, `statusView`) | агент |
| `src/features/` | start-application, fill-profile, upload-document, submit-application, take-next, review-document, decide-application, request-resubmit, export-documents | агент |
| `src/widgets/` | apply-shell, document-checklist, operator-shell (`ModelStatusBadge`), document-viewer, fields-panel (J/K/E, «Показать»), history-timeline | агент |
| `src/pages/` | home, apply (Start, Application), operator (Queue, Application, Expiring, Metrics, Callback), not-found | агент |
| `scripts/` | `gen-api.mjs`, `smoke-ui.mjs` (скриншоты в /tmp/e2e-*.png) | агент |
| `Dockerfile` (+ `Dockerfile.dockerignore`) | образ caddy со статикой | агент |

## 3. Решения модуля
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | Дизайн-система, токены и стек — как в «Проходной» | одна команда, одни навыки, меньше решений | ADR-012 |
| до Т0 | Ключ заявки — во фрагменте ссылки `#k=`, в памяти | не попадает в логи сервера и Referer | ADR-008 |
| до Т0 | ~~Просмотр PDF — `pdfjs-dist` (Apache-2.0)~~ | заменено строкой ниже: intake отдаёт PNG | DECISIONS §Лицензии |
| 2026-10-09 | Запросы — своя обёртка над fetch (`shared/api/http.ts`), не axios. problem+json → `ApiError {status, code, message=detail, hint}` | меньше зависимостей, единый формат ошибок | rules.md |
| 2026-10-09 | Вход оператора — `oidc-client-ts`, PKCE, client `documents-operator`, realm `documents`, redirect `/operator/callback`. Токены в `InMemoryWebStorage`: после перезагрузки — повторный вход (Keycloak по SSO пускает без пароля) | токены не переживают вкладку | DECISIONS §Лицензии |
| 2026-10-09 | Тихое обновление через iframe невозможно (CSP `frame-ancestors 'none'`) — обновление через refresh_token | CSP | infra/caddy |
| 2026-10-09 | Ключ заявки из `#k=` → память + sessionStorage, сразу стирается из адресной строки; заголовок `Authorization: Applicant <key>` | не в логах, Referer, истории | ADR-008 |
| 2026-10-09 | Камера — `<input capture="environment">` (системная камера); снимок сжимается до 4 МП (`shared/lib/image.ts`) | работает на любом телефоне, без getUserMedia | FE-AP-05 |
| 2026-10-09 | Просмотр документа — PNG-страницы intake (`/intake/api/v1/files/{id}/content?page=`) как blob, `revokeObjectURL` при уходе; pdfjs не нужен | меньше бандл, одинаково для PDF и фото | FE-OP-03 |
| 2026-10-09 | Подсветка поля: bbox `[x0,y0,x1,y1]` в координатах `DocumentCard.pages[{n,width,height}]` → проценты поверх `<img>` | не зависит от зума | X-03 |
| 2026-10-09 | Токены — генератор скилла (`gen:tokens`, guard `--check`). Недостающие утилиты sandbox (`bg-brand-deep`, `bg-sheen`, `bg-glow`, `bg-brand-conic`, `scrollbar-none`, `pb-nav`, `max-h-sheet`, `--tracking-*`) — вручную в `src/app/styles/index.css`; `theme.css` не править | генератор sandbox новее, guard сверяет со скиллом | ADR-012 |
| 2026-10-09 | Тема — в localStorage под `documents.theme` (не секрет); inline-скрипта против мигания нет (CSP `script-src 'self'`), тема применяется в `main.tsx` до рендера | CSP | — |
| 2026-10-09 | SSE: билет → `EventSource(?ticket=)`, обрыв → новый билет с нарастающей задержкой и перезапрос данных экрана | Last-Event-ID не нужен | ADR-009 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-09 | Первые запросы рабочего места уходили без токена (эффекты детей раньше эффекта оболочки) → 401 «через раз» | `setAuth` вызывается синхронно в `useOperatorAuth`, токен обновляется до запроса (FE-UI-01) |
| 2026-10-09 | Шрифты SB Sans на стенде не грузились: CSP SPA `style-src 'self'` резал CDN | В Caddyfile `style-src`/`font-src` + https://cdn-app.sberdevices.ru (FE-UI-02) |
| 2026-10-09 | Фрейм входа не менял высоту после перехода между страницами Keycloak | Счётчик загрузок фрейма в зависимостях ResizeObserver (FE-UI-02) |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-09 · miroslavrov · docs: документы модуля и DESIGN.md (ADR-012)
- 2026-10-09 · агент · feat: кабинет соискателя и рабочее место оператора, Dockerfile (FE-*)
- 2026-10-09 · агент · feat(FE-UI-01): паритет с песочницей, тема Keycloak, адаптив, проверка контакта, токен до первых запросов
- 2026-10-09 · агент · feat(FE-UI-02): вход на странице во фрейме, вход по коду, русские ошибки, анимации списков
- 2026-10-09 · агент · feat(FE-UI-03): вход «логин + код» своей формой, раздел «Сотрудники»; oidc-client-ts убран
