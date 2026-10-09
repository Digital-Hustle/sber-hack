# TASKS — frontend

Формат и правила — `../../docs/WORKFLOW.md §2`. Статус (последний столбец): `todo` → `doing \`ветка\`` → `review #PR` → `done #27` | `blocked: …`. Статус меняешь в своей ветке вместе с кодом; чужие строки не трогаешь. Новая задача — строкой в конец своей секции, ID не переиспользуем. Время — от Т0 (PLAN.md).

## Каркас и shared (F2) — первые 45 минут, остальные ждут FE-S-01..03
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-S-01 | — | Vite + React 19 + TS strict + Tailwind 4 + shadcn в эту папку (не удалять docs/), ESLint/Prettier, husky, скрипты `gen:api`, `gen:tokens`, `typecheck` | F2 | done #27 (без husky/prettier и shadcn — компоненты из sandbox) |
| FE-S-02 | — | `shared/config/tokens.ts` + `motion.ts` из скилла, `npm run gen:tokens`, `MotionProvider`, шрифты SB Sans | F2 | done #27 |
| FE-S-03 | FR-14/50 | `shared/api/$api` (problem+json, refresh), `oidc-client-ts`, `applicationKey`, `RequireAuth`/`RequireRole`/`RequireApplicationKey`, роутер зон | F2 | done #27 |
| FE-S-04 | — | `shared/ui`: Button, Input, Card, Badge, Dialog/Sheet, Select/Combobox, Tabs, Table, Toast, Skeleton, Empty/Error/Offline, Progress, Dropzone | F2 | done #27 |
| FE-S-05 | FR-12/57 | `shared/api/sse.ts` + `useEventStream` (ticket, backoff, Last-Event-ID), `useOnline`, `ErrorBoundary`; entities Application, Document (`stageView`, `reasonView`), DocType, ParsedField | F2 | done #27 (вместо Last-Event-ID — перезапрос после переподключения) |

## Кабинет соискателя `/apply` (F1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-AP-01 | FR-14 | `start-application`: согласие → заявка → ссылка-ключ и код возобновления; вход по ссылке/коду | F1 | done #27 |
| FE-AP-02 | FR-10 | `fill-profile`: анкета (гражданство, цель, срок) → `DocumentChecklist` с прогрессом | F1 | done #27 |
| FE-AP-03 | FR-11/05 | `UploadSlot` + `upload-document`: drag&drop/выбор, несколько страниц, отказ intake в пункте с подсказкой | F1 | done #27 |
| FE-AP-04 | FR-12 | `DocumentStatusCard`: стадии по SSE, причины непригодности и подсказки, `TYPE_MOVED` с переездом | F1 | done #27 |
| FE-AP-05 | FR-11 | `capture-document` + `CameraCapture`: камера, рамка, советы, сжатие снимка | F1 | done #27 (системная камера через input capture) |
| FE-AP-06 | FR-04 | `replace-file`/`remove-file`, дозагрузка страниц, запрос «переснять» от оператора | F1 | done #27 |
| FE-AP-07 | FR-15/B-01 | `CompletenessSummary`: «комплект полный / не хватает X, Y» из ответа бэка, решение по заявке | F1 | done #27 |
| FE-AP-08 | — | Полировка телефона: 390×844, safe-area, офлайн-баннер, тексты, прогон DEMO-сценария соискателя | F1 | done #27 |

## Рабочее место оператора `/operator` (F2)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-OP-01 | FR-50 | Layout `/operator`: SSO, `OperatorSidebar` по ролям, `ModelStatusBadge`, тема | F2 | done #27 |
| FE-OP-02 | FR-51 | `OperatorQueue`: статусы, ожидание, «есть что перепроверить», фильтры в URL, курсоры, `take-next` | F2 | done #27 |
| FE-OP-03 | FR-52 | `DocumentViewer`: страницы PDF/изображений (blob), зум, поворот, слой bbox | F2 | done #27 (PNG-страницы intake, без pdfjs) |
| FE-OP-04 | FR-52/B-02 | `FieldsPanel`: поля с уверенностью и проверками, «перепроверить» сверху, клик → bbox | F2 | done #27 |
| FE-OP-05 | FR-53 | `correct-field`, `correct-type`, `confirm-document`, `decide-application` | F2 | done #27 |
| FE-OP-06 | FR-54 | Горячие клавиши, «следующая заявка», счётчик «сэкономлено минут» | F2 | done #27 |
| FE-OP-07 | FR-55/30 | `request-resubmit`, `reprocess-document`, стадии в карточке по SSE | F2 | done #27 |
| FE-OP-08 | B-03/B-08 | `CrossCheckPanel` (расхождения между документами), `HistoryTimeline` | F2 | done #27 |
| FE-OP-09 | B-04/B-05 | Истекающие документы + `export-expiring`, `export-hr-card` | F2 | done #27 |
| FE-OP-10 | B-06/B-07 | `QueueMetrics` с «как посчитано», маскирование ПДн для AUDITOR | F2 | done #27 |
| FE-UI-01 | — | Паритет с песочницей: токены/анимации/компоненты, главная с артами и QR, вход в стиле песочницы и тема Keycloak, тихий вход после перезагрузки, адаптив (сводка очереди на десктопе, мобильные списки), скелетоны страниц, проверка телефона/почты, без демо-элементов | F2 | done #94 |
| FE-UI-02 | — | Вход прямо на странице (форма Keycloak во фрейме, без ухода с сайта), вход «логин → код из приложения», запоминание логина, шрифты SB Sans в CSP, русские тексты ошибок и видов документов, анимации списков/фильтров/вкладок | F2 | done #101 |
| FE-OP-11 | FR-54 | Переключатель «только требующие проверки» в панели полей; текст замечания `NUMBER_ELSEWHERE` | F2 | done #64 |
