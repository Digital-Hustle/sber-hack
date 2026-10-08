---
name: frontend-fsd
description: Use for ANY frontend change in a React + TypeScript + Vite project organised by Feature-Sliced Design (layers app/pages/widgets/features/entities/shared), including proxodnaya/frontend. Covers where a file goes, how to name slices, entity API (dto → mapper → model → TanStack Query key factory), feature mutations with optimistic updates, pages with lazy loading and route config with role guards, i18n namespaces, shadcn/Tailwind UI, axios instance with refresh, zustand stores, and the fsd-check import linter. Trigger on "сделай экран", "добавь страницу", "новая фича", "добавь сущность", "подключи эндпоинт на фронте", "куда положить компонент", "почини импорты", "ревью фронта".
---

# frontend-fsd

Эталон — `MelevaTe/hackathon-spring-2026-frontend` (выигранный хакатон команды). Здесь его правила, очищенные от найденных там нарушений. Для «Проходной» поверх этого действуют `proxodnaya/rules.md` и `references/adaptation.md`.

## 0. Перед изменением
1. `cat package.json` — убедись, что стек тот же (React 19, Vite, TS strict, Tailwind 4 + shadcn, axios, TanStack Query, zustand, react-hook-form, i18next). Если другой — не тащи эти правила вслепую.
2. Определи **слой** и **слайс** по таблице ниже. Сомневаешься между слоями — бери нижний (entities < features < widgets), поднимешь позже.
3. Шаблоны файлов — `references/templates.md`. Копируй их, не изобретай форму.

## 1. Слои (сверху вниз; импортировать можно только из слоёв НИЖЕ)
| Слой | Что лежит | Имена слайсов | Сегменты |
|---|---|---|---|
| `app/` | провайдеры, роутер, глобальные стили, `main.tsx` | — | `providers/<Name>/`, `styles/` |
| `pages/` | экран целиком = один маршрут; только компоновка виджетов | `PascalCase` + `Page` (`WorkerListPage`) | `ui/X.tsx`, `ui/X.async.tsx`, `index.ts` |
| `widgets/` | самостоятельные блоки страницы (сайдбар, таблица с фильтрами) | `PascalCase` | `ui/`, `model/` |
| `features/` | одно пользовательское действие с побочным эффектом | `kebab-case` глагол-сущность (`create-worker`, `revoke-device`) | `api/mutations.ts`, `model/`, `lib/`, `ui/` |
| `entities/` | бизнес-сущность: тип, загрузка, отображение без действий | `PascalCase` ед. ч. (`Worker`, `PassAttempt`) | `api/dto.ts`, `api/<x>Api.ts`, `api/queries.ts`, `lib/mappers.ts`, `model/types`, `model/consts`, `ui/`, `index.ts` |
| `shared/` | без бизнес-смысла: ui-кит, axios, конфиг, утилиты, роуты-константы | сегменты, не слайсы | `api/`, `config/`, `const/`, `hooks/`, `lib/`, `types/`, `ui/<Comp>/<Comp>.tsx` |

## 2. Правила импортов (проверяет `scripts/fsd-check.mjs`)
- **layer-direction**: только вниз. `shared` не импортирует ничего, `entities` — только `shared`, …, `app` — всё. Нужна тема/роутер в виджете — значит это `shared` (`shared/lib/theme`, `shared/const/router`), а не `app`.
- **cross-slice**: слайс не импортирует соседа по слою. Два виджета нужны вместе → собирает страница. Две сущности связаны → передавай данные пропсами или вынеси общий тип в `shared/types` (`@x`-нотацию FSD не используем).
- **public-api**: чужой слайс — только через `@/layer/Slice` (его `index.ts`). Глубокий импорт `@/entities/Worker/ui/WorkerCard` запрещён. Исключение: `shared/*` импортируется по сегментам (`@/shared/ui/Button/Button`).
- **relative-inside-slice**: внутри своего слайса — только относительные пути (`../model/types`), не `@/features/same-slice/...`.
- **no-public-api**: у каждого слайса `entities|features|widgets|pages` есть `index.ts`, экспортирующий только нужное снаружи.

```bash
node .agents/skills/frontend-fsd/scripts/fsd-check.mjs <path/to/src>            # exit 1 при нарушениях
node .agents/skills/frontend-fsd/scripts/fsd-check.mjs <path/to/src> --warn-only
```

## 3. Данные
- **Сущность владеет чтением**: `dto.ts` (контракт бэка как есть) → `mappers.ts` (`mapWorkerDto`: нормализация, даты в `Date`, enum-строки в const) → `model/types` (то, с чем работает UI). Компоненты никогда не видят DTO.
- `<x>Api.ts` — объект `workerApi = { list, get, ... }` поверх `$api` из `shared/api`. Никаких `fetch`/`axios` в компонентах.
- `queries.ts` — фабрика ключей `workerKeys = { all, lists(), list(params), detail(id) }` + хуки `useWorkersQuery`. Маппинг в `queryFn` или `select`. Опрос — `refetchInterval`, не `setInterval`.
- **Фича владеет записью**: `features/<verb-noun>/api/mutations.ts` → `useCreateWorkerMutation`. После успеха `invalidateQueries({ queryKey: workerKeys.lists() })`. Оптимистично — только для обратимых действий (избранное, порядок), с `onMutate`/откатом в `onError`.
- Ошибки: `toast.error(t("errors.x"))` — текст из i18n или из `ProblemDetail.detail` ответа, не захардкоженная строка.
- Глобальное клиентское состояние — zustand-стор в слайсе-владельце (`entities/Session/model/sessionStore.ts`) с `devtools` и именами экшенов `"session/setUser"`. Серверные данные в zustand не копируем — это кэш TanStack Query.

## 4. UI
- Компоненты — `memo` + `interface XProps` + именованный экспорт; страницы — `default export` (для `lazy`).
- Каждый список/экран: skeleton (loading) / empty (текст из i18n) / error (кнопка «Повторить» → `refetch`) / data.
- shadcn-компоненты лежат в `shared/ui/<Comp>/<Comp>.tsx`, стили через `cn()` из `shared/lib/utils` и `cva`-варианты. Новые цвета — токены в CSS-переменных, не hex в className.
- Формы: `react-hook-form` + схема (`zod` через `@hookform/resolvers`), маппер «форма → DTO» в `features/x/lib/`.
- Тексты — только `t("key")`. Namespace = страница/фича (`public/locales/ru/workers.json`), общие — `common`. Ключи добавляй сразу в `ru` (и `en`, если есть).

## 5. Маршруты
- `shared/const/router.ts`: `AppRoutes` (const-объект) + `getRouteWorkers()`/`getRouteWorker(id)` — ссылки строим только через них.
- `app/providers/router/config/routeConfig.tsx`: `Record<AppRoutes, AppRouteProps>` с `path`, `element`, `authOnly`, `roles?`. Новый экран = новая запись + `getRouteX` + lazy-страница. Охрана — `RequireAuth`/`RequireRole` в `AppRouter`, не внутри страницы.

## 6. Проверка перед PR
```bash
npm run lint && npm run typecheck && npm run build
node .agents/skills/frontend-fsd/scripts/fsd-check.mjs src
```
Prettier (printWidth 90, двойные кавычки, один атрибут на строку, сортировка tailwind-классов) и ESLint `import/order` правят `npm run lint -- --fix` и husky/lint-staged.

## 7. Частые ошибки эталона (не повторять)
Найдено `fsd-check` в эталоне (8 на 250 файлов): `widgets/ThemeSwitcher` и `widgets/MindMapCanvas` импортируют из `app` (тема/провайдер → перенести в `shared`); `widgets/AppShell` импортирует соседний виджет `Navbar` (композицию — в странице или в `app`-layout); 4 alias-импорта внутри собственного слайса. Плюс: токены в `localStorage`, нефиксированные тексты в `toast`, смешанные имена слайсов фич (PascalCase и kebab-case) — у нас только kebab-case.
