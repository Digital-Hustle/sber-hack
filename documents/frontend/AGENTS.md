# AGENTS.md — frontend (одно Vite-приложение, FSD, зоны /apply и /operator)

Этот файл важнее `documents/AGENTS.md` для всего внутри `frontend/`. Красные линии `../rules.md §1` действуют всегда.

**Владельцы:** F1 — зона `/apply` (кабинет соискателя: pages/widgets/features зоны), F2 — `/operator`, `app`, `shared`, `entities`, `tokens.ts`, `package.json`.

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, известные баги. Потом `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя агенту во фронте.
3. Любая визуальная работа → **`docs/DESIGN.md`** (токены, компоненты, отступы). Никаких цветов/размеров/длительностей литералами.
4. Структура и потоки — `docs/ARCHITECTURE.md`; зачем и для кого — `docs/PRD.md`.
5. Скилл `../../.agents/skills/frontend-fsd/SKILL.md` (FSD, данные, UI) и процесс `../docs/WORKFLOW.md` (draft PR, handoff).

## Команды
```bash
npm ci && npm run gen:api && npm run gen:tokens
npm run dev                                  # https://localhost:5173/apply , /operator
npm run lint && npm run typecheck && npm run build
node ../../.agents/skills/frontend-fsd/scripts/fsd-check.mjs src
../scripts/ci-local.sh quick                 # то же, что CI, из documents/
```

## Не делай
- Не проверяй файлы «вместо сервера» и не решай тип документа на клиенте: клиентская проверка — только подсказка, отказ даёт intake (Д1).
- Не храни токены и ключ заявки в localStorage (ключ — во фрагменте ссылки `#k=` → память + `sessionStorage` только по ADR), не подключай моки вне `import.meta.env.DEV`.
- Не правь `src/app/styles/theme.css` руками — он генерируется из `src/shared/config/tokens.ts`.
- Не удаляй `docs/`, `AGENTS.md`, `CLAUDE.md` при скаффолдинге (Vite создаётся в эту же папку).
