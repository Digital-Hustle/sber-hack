# Каталог правил проверки пакета (гипотеза до текста кейса, PLT-00)

Каждое правило: **код → формула → параметры (`programs.params`) → статус → текст специалисту**. Реализация — `services/rules/…/rules/<Code>Rule.java` + тест с числами (граница, копейки). Деньги — `BigDecimal`, scale 2, допуск `TOLERANCE = 0.01`. Нет данных → `DATA_MISSING` (а не `PASSED`). Правило неприменимо по условию → `NOT_APPLICABLE`.

## Комплектность (С5–С7)
| Код | Проверка | Статус при нарушении |
|---|---|---|
| `CHK_REQUIRED` | для каждого пункта чек-листа есть документ нужного вида (по определённому типу, не по файлу) | FAILED: «Нет документа: {вид}» |
| `CHK_CONDITIONAL` | условный пункт (доверенность, отчётность старше 1 года) — условие вычислено по данным, документ есть | FAILED / NOT_APPLICABLE |
| `CHK_ONE_OF` | «договор **или** счёт» на каждую статью сметы | FAILED |
| `CHK_DUPLICATE` | два документа одного вида там, где нужен один | WARNING |
| `CHK_UNKNOWN` | есть `TYPE_UNKNOWN`/`UNREADABLE`/`OTHER` | WARNING: пакет → NEEDS_REVIEW |

## Реквизиты и сверка между документами (С8–С9, B-01)
| Код | Формула | Статус |
|---|---|---|
| `REQ_INN_CHECKSUM` | контрольная сумма ИНН в каждом документе (DOC-TYPES §Контрольные суммы) | FAILED |
| `REQ_OGRN_CHECKSUM` | ОГРН/ОГРНИП | FAILED |
| `REQ_BANK_KEY` | ключ БИК + р/с (заявление, платёжки) | FAILED |
| `CROSS_INN` | ИНН заявителя одинаков в заявлении, ЕГРЮЛ, справке ФНС, отчётности, договорах (покупатель), платёжках (плательщик) | FAILED: «ИНН в {doc A} ≠ {doc B}» |
| `CROSS_OGRN` / `CROSS_NAME` | ОГРН и наименование (нормализация кавычек, ОПФ «ООО»/«Общество с ограниченной ответственностью») | FAILED / WARNING |
| `CROSS_SIGNER` | подписант заявления = руководитель из ЕГРЮЛ или есть доверенность | FAILED |
| `CROSS_COUNTERPARTY` | продавец в счёте = исполнитель договора = получатель платежа (ИНН) | FAILED |

## Сроки и даты (С10)
| Код | Формула | Параметр | Статус |
|---|---|---|---|
| `DATE_EGRUL_FRESH` | `packageDate − extractDate ≤ egrulMaxAgeDays` | 30 | FAILED |
| `DATE_TAX_FRESH` | `packageDate − certificateDate ≤ taxMaxAgeDays` | 30 | FAILED |
| `TAX_NO_DEBT` | `hasDebt = false` | — | FAILED |
| `DATE_ORDER` | `contract.date ≤ invoice.date ≤ payment.date` | — | WARNING |
| `DATE_COST_PERIOD` | дата затрат в периоде программы `[costFrom, costTo]` | даты | FAILED |
| `ORG_ACTIVE` | статус в ЕГРЮЛ «действующее», нет ликвидации/банкротства | — | FAILED |
| `ORG_OKVED_ALLOWED` | основной ОКВЭД в списке программы | список | FAILED |

## Арифметика (С11, Д11 — только на сервере)
| Код | Формула | Статус |
|---|---|---|
| `SUM_INVOICE_ITEMS` | `Σ items.amount = total` (± 0.01); `qty × price = amount` по каждой позиции | FAILED: «Сумма позиций {a} ≠ итог {b}» |
| `SUM_VAT` | `total − vatAmount = amountWithoutVat`; `vatAmount = round(base × rate)` для ставок 0/10/20 % | WARNING |
| `SUM_ESTIMATE_TOTAL` | `Σ estimate.items = estimate.total` | FAILED |
| `SUM_ESTIMATE_CONFIRMED` | каждая статья сметы подтверждена счётом/договором на сумму ≥ статьи; `Σ подтверждённого ≥ estimate.total` | FAILED / WARNING |
| `SUM_PAID` | `Σ payments по счёту ≥ invoice.total` (если программа возмещает оплаченные затраты) | FAILED |
| `SUM_SUBSIDY_CAP` | `subsidy ≤ min(rate × eligibleCosts, maxAmount)` | FAILED: «Запрошено {a}, максимум {b} = min({rate} × {costs}, {cap})» |
| `SUM_REQUEST_MATCH` | `application.requestedAmount = estimate.subsidyAmount` | FAILED |
| `SUM_WORDS` | сумма прописью = цифрами (если есть) | WARNING |
| `FIN_BALANCE` | `1600 = 1700` в балансе | WARNING |

## Аномалии (B-06, только WARNING — специалист решает)
`ANOMALY_ROUND_SUMS` (все суммы круглые), `ANOMALY_WEEKEND_DATES` (платёж в выходной), `ANOMALY_SAME_DAY_CHAIN` (договор, счёт и оплата в один день на крупную сумму), `ANOMALY_OUTLIER` (статья > 3σ от средней по программе — только если в БД ≥ 20 пакетов, иначе NOT_APPLICABLE).

## Параметры программы (данные `programs.params`, версия в протоколе)
```json
{"rate": "0.50", "maxAmount": "3000000.00", "egrulMaxAgeDays": 30, "taxMaxAgeDays": 30,
 "costFrom": "2026-01-01", "costTo": "2026-12-31", "okvedAllowed": ["10.*", "25.*", "62.01"],
 "reimbursePaidOnly": true}
```
Демо-программа `DEMO-2026` создаётся миграцией с контекстом `demo` (не prod) и помечена «демо».
