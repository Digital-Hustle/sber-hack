# AGENTS.md — корень монорепы sber-hack

Монорепа хакатона. **Каждый кейс живёт в своей папке и самодостаточен**: свой backend, frontend, infra, docs, scripts.

| Папка | Кейс | С чего начать |
|---|---|---|
| `proxodnaya/` | «Проходная» (основной) | `proxodnaya/AGENTS.md` + скилл `.agents/skills/proxodnaya-dev/SKILL.md` |
| `documents/` | «Документы» | `documents/AGENTS.md` + скилл `.agents/skills/documents-dev/SKILL.md` |
| `subsidy/` | «Субсидии» | `subsidy/AGENTS.md` + скилл `.agents/skills/subsidy-dev/SKILL.md` |

Правила для всех:
1. Определи кейс по пути файлов задачи и работай только внутри его папки. Общие файлы корня (`.github/`, `.agents/`, этот файл) меняй только по прямой просьбе.
2. Следуй `AGENTS.md`, `rules.md` и скиллу своего кейса.
3. Только PR: ветка → проверки → `scripts/open-pr.sh` из папки кейса. Не апрувь сам; мержи только при явном делегировании (запись в `<кейс>/docs/MEMORY.md §3`).
4. Никаких секретов в репозитории.
5. Общие скрипты скиллов (`backend-spring/scripts/init-backend.sh`, `new-service.sh`, `be-check.mjs`) работают для любого кейса: кейс определяется по папке (`proxodnaya|documents|subsidy`), пакет — `ru.<кейс>`.
