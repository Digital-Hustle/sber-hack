# MEMORY — face

Память сервиса между сессиями и агентами. §1 — перезаписываемое текущее состояние (≤ 10 строк). §2 — карта, обновлять при новых пакетах/файлах. §3–4 — добавлять строки, неактуальное ~~зачёркивать~~. §5 — **только дописывать в конец** (`merge=union`). Решения для всей системы — ADR в `../../../docs/DECISIONS.md`.

## 1. Текущее состояние
- FACE-01..05/07 готовы: verify/enroll/check/delete, YuNet+SFace через OpenCV (без onnxruntime), AES-GCM эталоны, миграции своим раннером. Порт 8090.
- Дальше: FACE-08 калибровка порога на фото команды, FACE-06 MiniFASNet, FACE-09 OTel.
- Открытые вопросы: порог 0.363 (OpenCV Zoo) не откалиброван; зеркальность кадров киоска (`FACE_MIRRORED`) уточнить с фронтом.

## 2. Карта сервиса
| Путь | Что |
|---|---|
| `docs/` | PRD, ARCHITECTURE, RULES, TASKS, MEMORY |
| `src/face/main.py` | сборка приложения (`uvicorn face.main:app --factory`), `create_app` для тестов |
| `src/face/core/` | config (env), errors (problem+json), security (JWT по JWKS, роли) |
| `src/face/domain/` | model (типы, порты), quality, liveness, matcher, service (сценарии) |
| `src/face/infra/` | models (YuNet/SFace), fetch (скачать + sha256), images, crypto, repository, migrate |
| `src/face/api/` | routes, schemas (camelCase), metrics |
| `db/migrations/` | `NNN_*.sql`, учёт в `schema_migrations` |
| `tests/unit`, `tests/integration` | фейки моделей; настоящие модели + образцы OpenCV; своя временная БД |

## 3. Решения
| Дата | Решение | Почему | Ссылка |
|---|---|---|---|
| до Т0 | face на Python, сверка 1:1, косинус в numpy | см. ADR | ADR-025 |
| до Т0 | эмбеддинги в PostgreSQL, зашифрованы | см. ADR | ADR-009 |
| до Т0 | активная живость через челлендж | см. ADR | ADR-004 |
| 2026-10-09 | модели через cv2.FaceDetectorYN/FaceRecognizerSF, не onnxruntime | меньше зависимостей, выравнивание SFace из коробки | FACE-01 |
| 2026-10-09 | миграции — свой раннер SQL под advisory lock вместо Liquibase | в Python-образе нет JVM; одна таблица | FACE-03 |
| 2026-10-09 | эмбеддинги старой версии модели игнорируются → FACE_NOT_ENROLLED | смена модели требует переснять эталон | FACE-04 |

## 4. Известные баги и ограничения
| Дата | Что | Обход / статус |
|---|---|---|
| 2026-10-09 | BLINK не определяется по 5 точкам YuNet | access выдаёт только TURN_LEFT/TURN_RIGHT/NOD; FACE-10 |
| 2026-10-09 | Неподвижное фото проходит match (живость ловит только движение головы) | MiniFASNet — FACE-06 |

## 5. Журнал изменений (дописывать в конец)
- 2026-10-08 · miroslavrov · docs: документы сервиса (ADR-034)
- 2026-10-09 · miroslavrov · FACE-01..05/07: сервис face (verify/enroll/check/delete, живость по позе головы)
