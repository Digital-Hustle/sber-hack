# TASKS — frontend

Формат и правила — `../../docs/WORKFLOW.md §2`. Статус: `todo` → `doing \`ветка\`` → `review #PR` → `done #PR` | `blocked: …`. Статус меняешь в своей ветке; чужие строки не трогаешь. Новая задача — в конец секции, ID не переиспользуем.

## Каркас и shared (F2) — первые 45 минут
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-S-01 | — | Vite + React 19 + TS strict + Tailwind 4 + shadcn в эту папку, ESLint/Prettier, `gen:api`, `gen:tokens`, `typecheck` | F2 | todo |
| FE-S-02 | — | `tokens.ts` + `motion.ts` из скилла, `gen:tokens`, шрифты SB Sans | F2 | todo |
| FE-S-03 | FR-60/66 | `$api` (problem+json, refresh), `oidc-client-ts`, `RequireAuth`/`RequireRole`, роутер `/specialist` | F2 | todo |
| FE-S-04 | — | `shared/ui`: Button, Input, Card, Badge, Dialog/Sheet, Select/Combobox, Tabs, Table, Toast, Skeleton, Empty/Error/Offline, Dropzone | F2 | todo |
| FE-S-05 | FR-62 | `sse.ts` + `useEventStream`, `useOnline`, `ErrorBoundary`; entities Package, PackageFile, Document, ParsedField, Protocol, RuleResult; `shared/lib/money.ts` (только формат) | F2 | todo |

## Страница пакета (F1)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-PK-01 | FR-01 | `create-package`: ИНН (маска; проверка — сервер) + программа → пакет | F1 | todo |
| FE-PK-02 | FR-02/06 | `UploadDropzone` + `upload-files`: файлы и ZIP, прогресс, `rejected[]` у файла | F1 | todo |
| FE-PK-03 | FR-62 | `FileList`: файлы → документы (сегменты), вид, уверенность, стадия по SSE | F1 | todo |
| FE-PK-04 | FR-63 | `DocumentViewer`: страницы PDF/изображений (blob), зум, поворот, bbox-слой | F1 | todo |
| FE-PK-05 | FR-63/33 | `FieldsPanel`: поля с уверенностью и проверками, клик → bbox | F1 | todo |
| FE-PK-06 | FR-64 | `change-type` и `correct-field` → ожидание `package.checked` | F1 | todo |
| FE-PK-07 | FR-65 | `replace-file`, `remove-file`, версии файла | F1 | todo |
| FE-PK-08 | B-08 | Сортировка «сомнительные сверху», фильтр по виду, горячие клавиши | F1 | todo |

## Протокол, очередь и аналитика (F2)
| ID | FR | Задача | Роль | Статус |
|---|---|---|---|---|
| FE-SP-01 | FR-60 | Layout: SSO, `AppSidebar` по ролям, `ModelStatusBadge`, тема | F2 | todo |
| FE-SP-02 | FR-61 | `PackagesTable`: статусы, «не хватает», ошибки, фильтры в URL, курсоры | F2 | todo |
| FE-SP-03 | FR-67 | `ProtocolPanel` + `CompletenessCard`: группы статусов, expected/actual, evidence → документ | F2 | todo |
| FE-SP-04 | FR-46 | Версии протокола, `decide-package` (SENIOR) | F2 | todo |
| FE-SP-05 | B-02 | `export-protocol` DOCX/XLSX/PDF | F2 | todo |
| FE-SP-06 | B-07 | «Чего не хватает» + текст запроса организации (копировать) | F2 | todo |
| FE-SP-07 | B-03 | `OrganizationPage` + `HistoryTimeline` | F2 | todo |
| FE-SP-08 | B-04 | `StatsPage`: `ErrorStatsChart` с «как посчитано» | F2 | todo |
| FE-SP-09 | B-05 | `ExpiringPage`: истекающие справки и выписки | F2 | todo |
| FE-SP-10 | B-06 | `AssistantChat`: ответ + источники + статус валидатора | F2 | todo |
