# MEMORY — assistant

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- Есть (AS-01/02/04/05/07/09): `POST /assistant/ask`, `GET /assistant/status`; один адаптер `OpenAiChatGateway` (Ollama — через `/v1`, OpenAI-совместимые), CB `llm`, таймаут вызова `LLM_TIMEOUT_SECONDS`.
- Инструменты (`on_site_now`, `worked_hours`, `late_overtime`, `refusals`) — публичная аналитика shift с токеном спросившего; часы и проценты досчитывает сервер (`workedHours`, `*Percent`, `totals`).
- `NumberValidator`: числа ответа ⊂ данные инструментов + вопрос (+ округления, доли → %); даты, время, id, пункты списка не проверяются. Лишнее → одна перегенерация → отказ с `data`.
- Журнал `llm_requests`: вызов модели, вопрос — только SHA-256, `validation` — итог на все вызовы вопроса.
- Дальше: GigaChat (AS-03), `table`/поток SSE (AS-06), PromptInjectionTest (AS-08), инсайты (AS-10). Открытый вопрос: ключ GigaChat.

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `llm/` | порт `LlmGateway`, `ChatRq/ChatRs/ChatMessage/ToolCall/ToolSpec`; `llm/infra/openai` — единственный HTTP к провайдеру (ArchUnit) |
| `gateway/ShiftClient` | GET к shift с Bearer спросившего |
| `features/assistant/application/tools` | `AssistantTool`, `Period` (даты в `SITE_ZONE`, ≤ 93 дней), четыре инструмента |
| `features/assistant/validator` | `NumberExtractor`, `NumberValidator` |
| `features/assistant/command` | `AssistantCommandService` (цикл ≤ 3, перегенерация, отказ), `LlmRequestRepository` |
| `features/assistant/query` | `LlmStatusQueryService` — `/assistant/status` по журналу |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | LLM за портом, три адаптера, локально Ollama | см. ADR | ADR-005 |
| до Т0 | помощник только через инструменты + валидатор чисел | см. ADR | ADR-006 |
| 2026-10-09 | инструменты читают публичную аналитику shift токеном спросившего, а не service-токен `/internal` | помощник видит ровно то, что разрешено пользователю; новые internal-эндпоинты не нужны | #PR |
| 2026-10-09 | один OpenAI-совместимый адаптер вместо отдельного Ollama | Ollama отдаёт тот же `/v1/chat/completions` с tools | #PR |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-09 | ответ без потока: `history`, `table`, SSE ещё нет; при таймауте модели — 503, без частичного ответа | AS-06 |
| 2026-10-09 | люди в ответе — по `workerId`: аналитика shift не отдаёт ФИО | обогатить из `workers` shift |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-08 · miroslavrov · docs: документы сервиса (ADR-034)
- 2026-10-09 · miroslavrov (агент) · feat: AS-01/02/04/05/07/09 помощник — `/assistant/ask` (tool calling, проверка чисел), `/assistant/status`, журнал `llm_requests` (#PR)
