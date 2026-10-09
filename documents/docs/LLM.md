# Слой модели и локальный запуск (Д5, Д6, разд. 5.3)

## Изоляция
```
docproc ──► LlmGateway  (libs/llm; интерфейс)
              chat(messages) → ChatResult
              structured(messages, jsonSchema) → JsonNode   // ответ валиден по схеме или исключение
              vision(messages, images, jsonSchema) → JsonNode
              status() → {provider, model, reachable}
   ▲
   ru.documents.llm.infra.gigachat.GigaChatAdapter
   ru.documents.llm.infra.openai.OpenAiCompatAdapter     // любой OpenAI-совместимый
   ru.documents.llm.infra.ollama.OllamaAdapter           // format: <json schema>, keep_alive
```
- Бин выбирается `@ConditionalOnProperty(name = "app.llm.provider")`. Вне `ru.documents.llm.infra` никто не знает о провайдерах (ArchUnit `LlmIsolationTest` + `guard.sh`).
- Общее для всех адаптеров (`DefaultLlmGateway`): таймаут, retry ×2 с экспоненциальной паузой, честный семафор `LLM_MAX_CONCURRENCY`; вместо circuit breaker — при `ModelUnavailableException` задание docproc уходит в `WAITING_MODEL` и откладывается на `waiting-model-retry`, после восстановления модели догоняет само, метрики `llm_request_seconds{provider,model,outcome}`, запись в `llm_calls` (через SPI, который реализует docproc).
- Ответ проверяется по JSON Schema (networknt json-schema-validator, Apache-2.0). Невалиден → одна перегенерация с текстом ошибки → `MODEL_INVALID_OUTPUT` → `NEEDS_REVIEW`. Модель недоступна / CB открыт → `ModelUnavailableException` → стадия `WAITING_MODEL` (не «готово», СН).

## Настройки (`infra/.env`)
```
LLM_PROVIDER=ollama              # gigachat | openai | ollama
LLM_BASE_URL=http://ollama:11434
LLM_MODEL=qwen3:8b               # текст: классификация и извлечение
LLM_VISION_MODEL=qwen2.5vl:7b    # картинка страницы, если OCR слабый (VISION_FALLBACK_ENABLED)
LLM_API_KEY=                     # только для облака, только в .env
LLM_TIMEOUT_SECONDS=60
LLM_MAX_CONCURRENCY=2
GIGACHAT_SCOPE=GIGACHAT_API_PERS
```

## Режимы
| Режим | Когда | Как включить |
|---|---|---|
| **API** | разработка, стенд, CI-smoke, если дадут ключ | `LLM_PROVIDER=openai` (+ `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY`) или `gigachat` |
| **Локально (Д6)** | показ без интернета, проверка судьями | `LLM_PROVIDER=ollama` + `docker compose --profile llm-local up -d` |

OpenAI-совместимые: DeepSeek, Qwen (DashScope), GLM (Z.ai), vLLM. Требования к модели: русский язык, structured output / JSON mode, контекст ≥ 16k; для vision-пути — модель с картинками.

## Выбор локальной модели (заполнить замером на открытом наборе, FR-42)
Замер — `eval/run.py` через публичный API стенда при нужном `LLM_MODEL` (см. `EVAL.md`): для каждой модели перезапустить docproc с новым `LLM_MODEL`, прогнать `--set open` и `--set own`, перенести из `eval/reports/` accuracy типа, exact match полей и p50 времени. Пока стенда с открытым набором не было — ячейки «_замер_» не заполнены.
| Модель (Ollama) | Лицензия | RAM | Время на документ, CPU | Точность типа | Точность полей | Вывод |
|---|---|---|---|---|---|---|
| `qwen3:8b` | Apache-2.0 | ~6 ГБ | _замер_ | _замер_ | _замер_ | основной кандидат (текст) |
| `qwen3:4b` | Apache-2.0 | ~3 ГБ | _замер_ | _замер_ | _замер_ | если мало RAM |
| `qwen2.5vl:7b` | Apache-2.0 | ~6 ГБ | _замер_ | _замер_ | _замер_ | vision для фото |
| `qwen3-vl:8b` | Apache-2.0 (проверить карточку) | ~6 ГБ | _замер_ | _замер_ | _замер_ | альтернатива vision |
| ~~`qwen2.5vl:3b`~~ | Qwen Research (некоммерч.) | — | — | — | — | **нельзя** (п. 5.8.4) |
Перед `ollama pull` любой новой модели — проверить лицензию в карточке и записать строку в `DECISIONS.md`.

## Локальный провайдер — инструкция для судей
1. `scripts/gen-env.sh` (или `cp infra/.env.example infra/.env`) и `LLM_PROVIDER=ollama`.
2. `cd infra && docker compose --profile llm-local up -d` — поднимется `ollama`, init-контейнер `ollama-pull` скачает `LLM_MODEL` и `LLM_VISION_MODEL` (~5 + 6 ГБ).
   Без интернета: `docker load < ollama-image.tar` и том `ollama-models` из архива (`infra/ollama/README.md`).
3. Проверка: `docker compose exec ollama ollama list` — модели в списке; в рабочем месте оператора бейдж «Ollama · qwen3:8b · доступна».
4. Требования: 16 ГБ RAM, 4+ CPU; с NVIDIA GPU — раскомментировать `deploy.resources` в compose.
5. Демо: выключить интернет → загрузить документ → стадии идут, поля извлечены; `docker compose stop ollama` → `WAITING_MODEL`; `start` → догнал сам.

## Промпты и схемы (FR-45)
- Файлы: `services/docproc/src/main/resources/prompts/{classify,extract}.v<N>.md` (системный) и `*.v<N>.user.md` (шаблон пользовательского сообщения); сейчас v1, версия пишется в результат (`promptVersion`); JSON Schema полей — из `doc_types.schema` (версия в результате).
- **Классификация**: системный промпт перечисляет типы реестра с короткими описаниями и признаками из `DOC-TYPES.md`; вход — первые ~4000 символов текста, флаги (`mrzFound`, найденные форматы номеров); выход `{type, confidence, evidence: [строки текста]}`. `evidence` должны встречаться в тексте (проверка кодом), иначе уверенность понижается.
- **Извлечение**: только поля схемы типа; правило «значение — только из текста документа, нет — `null` с причиной»; для каждого поля `raw` (дословно из текста) → код находит `raw` в словах OCR и берёт bbox; не нашёл — уверенность ниже.
- Few-shot — только синтетические примеры из `eval/synthetic/`, никогда из открытого набора (rules.md §1.3).
- Детерминизм: Ollama — `temperature 0`, фиксированный `seed`, без «размышлений»; OpenAI-совместимые — `temperature 0`; GigaChat — `0.01`.

## Чего не делаем
Заготовленные ответы и fallback с данными; передачу модели имени файла и пункта загрузки; хранение текстов документов в логах; прямой доступ модели к БД.
