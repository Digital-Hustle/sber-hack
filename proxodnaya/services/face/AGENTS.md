# AGENTS.md — сервис face (Python 3.12, FastAPI, onnxruntime)

Этот файл важнее `proxodnaya/AGENTS.md` для всего внутри `services/face/`. Красные линии `../../rules.md §1` действуют всегда.

**Владелец:** B2. **Порт:** 8090. **БД:** `face`. Сверка лица 1:1 и живость. **Решений о проходе не принимает.**

## Прочитай перед задачей (по порядку, только нужное)
1. `docs/MEMORY.md` — текущее состояние, карта, баги. Затем `docs/TASKS.md` — твоя строка.
2. `docs/RULES.md` — что можно/нельзя в этом сервисе.
3. `docs/ARCHITECTURE.md` — поток, данные, события, зависимости; `docs/PRD.md` — зачем.
4. Скилл `../../../.agents/skills/backend-spring/SKILL.md` (раздел Python face) и процесс `../../docs/WORKFLOW.md` (draft PR, handoff, конфликты).

## Команды
```bash
# из services/face/
uv sync && uv run ruff check . && uv run ruff format --check . && uv run mypy src && uv run pytest -q
uv run uvicorn face.main:app --port 8090
(cd ../.. && scripts/new-migration.sh face <slug>)      # → services/face/db/changelog/
(cd ../.. && scripts/ci-local.sh quick)   # те же проверки, что в CI
```

## Не клади в git
Модели ONNX (скачиваются скриптом по хэшу), фото людей, эмбеддинги, тестовые лица команды. InsightFace — нельзя (веса non-commercial).
