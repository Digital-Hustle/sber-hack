# Слой модели и локальный запуск (Д5, Д6, разд. 5.3)

## Изоляция
```
assistant-service: assistant ──► LlmGateway   (интерфейс: chat(messages, tools) → ChatResult)
                 ▲
   llm.infra.gigachat.GigaChatAdapter
   llm.infra.openai.OpenAiCompatAdapter
   llm.infra.ollama.OllamaAdapter
```
- Бин выбирается через `@ConditionalOnProperty(name="app.llm.provider")`.
- Вне `llm.infra` никто не знает о провайдерах (проверяет ArchUnit-тест `LlmIsolationTest`).
- Общие для всех адаптеров: таймаут, retry ×1, circuit breaker, метрики `llm_request_seconds{provider,model,outcome}`, запись в `assistant_log`.
- `GET /api/assistant/status` → `{provider, model, reachable}`; бейдж показывается в админке.

## Настройки (`.env`)
```
LLM_PROVIDER=ollama            # gigachat | openai | ollama
LLM_BASE_URL=http://ollama:11434
LLM_MODEL=qwen3:4b
LLM_API_KEY=                   # только для облака, только в .env
LLM_TIMEOUT_SECONDS=30
GIGACHAT_SCOPE=GIGACHAT_API_PERS
```

## Два режима запуска: API (по умолчанию для разработки) и локально (для защиты, Д6)
| Режим | Когда | Как включить |
|---|---|---|
| **API** | разработка, тесты, стенд, CI-smoke | `LLM_PROVIDER=openai` + `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` в `.env`; Ollama не поднимается |
| **Локально** | показ судьям без интернета, проверка Д6 | `LLM_PROVIDER=ollama` + `docker compose --profile llm-local up -d` |
| GigaChat | если дадут ключ на хакатоне | `LLM_PROVIDER=gigachat` |

Адаптер `openai` — любой OpenAI-совместимый Chat Completions с function calling, поэтому подходят DeepSeek (`https://api.deepseek.com/v1`, `deepseek-chat`), Qwen через DashScope (`https://dashscope-intl.aliyuncs.com/compatible-mode/v1`, `qwen-plus`/`qwen3-*`), GLM через Z.ai (`https://api.z.ai/api/paas/v4`, `glm-4.6`). Требования к модели: русский язык, tool calling, контекст ≥ 32k; vision не нужна. Переключение — только переменными, код не меняется; обязательный локальный режим остаётся рабочим и проверяется перед защитой.

## Локальный провайдер — инструкция для судей
1. `cp infra/.env.example infra/.env` и выставить `LLM_PROVIDER=ollama`.
2. `docker compose --profile llm-local up -d` — поднимется `ollama`, init-контейнер скачает модель `qwen3:4b` (~2.5 ГБ).
   Офлайн-вариант: `docker load < ollama-image.tar` и том с заранее скачанной моделью.
3. Проверка: `curl localhost:11434/api/tags` — модель в списке; в админке бейдж «Ollama · qwen3:4b · доступна».
4. Требования: 8 ГБ RAM, CPU; с NVIDIA GPU раскомментировать `deploy.resources` в compose и взять `qwen3:8b`.
5. Демо: отключить интернет → задать помощнику вопрос → ответ с источниками.

## Помощник: как устроен
1. Системный промпт: роль, запрет придумывать числа, «отвечай только по результатам инструментов», язык — русский, `/no_think` для qwen3.
2. Инструменты (числа считает сервер; это read-only вызовы shift-service и access-service через service account):
   `presence_now(zone?)`, `worker_find(query)`, `hours_worked(period, worker?|contractor?)`, `late_and_overtime(period)`, `attempts_stats(period, reason?)`, `load_by_hour(period)`, `alerts(period)`, `shift_status(worker)`.
3. Цикл: модель → tool calls → сервер выполняет → модель формирует ответ (максимум 3 итерации).
4. **Валидатор**: из ответа извлекаются числа и даты; каждое число должно встречаться в JSON-результатах инструментов (допускаются тривиальные суммы и проценты, пересчитанные сервером). Не прошёл → одна перегенерация с замечанием → иначе «Не могу подтвердить ответ данными» + таблица сырых результатов.
5. Ответ UI: `{text, sources:[{tool, args, asOf}], table?, validation:"ok|regenerated|blocked"}`.
6. Fallback для моделей без function calling: JSON-план `{"tool":..., "args":...}` по строгой схеме.
7. Проактивность (B-09): `@Scheduled` каждые 15 мин → детекторы аномалий на правилах (опоздания > N, скопления, рост отказов, человек без выхода) → модель пишет короткую сводку только из этих чисел → валидатор → лента «Помощник заметил».

## Вопросы для демо
- «Сколько людей сейчас на объекте и в каких зонах?»
- «Кто опоздал сегодня больше чем на 15 минут?»
- «Почему у Иванова вчера был отказ на входе?»
- «Сколько часов отработала бригада ООО Стройка за неделю?»
- Ловушка: «Сколько человек было вчера в зоне Крыша?» (такой зоны нет) → «Зоны с таким названием нет».
