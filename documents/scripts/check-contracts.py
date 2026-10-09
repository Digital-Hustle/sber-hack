#!/usr/bin/env python3
"""Сверка контрактов (PLT-04, X-02): коды ошибок и стадии в коде ↔ docs/API.md ↔ фронт.

1. Каждый код DomainException в Java-сервисах описан в API.md (таблица `| CODE | HTTP | ...`).
2. Каждая строка таблиц API.md (коды, стадии, причины) встречается в коде бэкенда.
3. Каждая стадия документа из API.md известна фронту (frontend/src).
Запуск: python3 documents/scripts/check-contracts.py (из корня репо или из documents/).
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
api = (ROOT / "docs/API.md").read_text(encoding="utf-8")
rows = set(re.findall(r"^\| ([A-Z][A-Z_]{3,}) \|", api, re.M))
stages_block = api.split("## Стадии документа", 1)[1].split("\n## ", 1)[0]
stages = set(re.findall(r"^\| ([A-Z][A-Z_]{3,}) \|", stages_block, re.M))

java = [p for p in (ROOT / "services").glob("*/src/main/java/**/*.java")]
backend = "\n".join(p.read_text(encoding="utf-8") for p in java)
thrown = set(re.findall(r'DomainException(?:\.\w+)?\(\s*"([A-Z][A-Z0-9_]+)"', backend))
thrown |= set(re.findall(r'String CODE\s*=\s*"([A-Z][A-Z0-9_]+)"', backend))
front = "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "frontend/src").rglob("*.ts*"))

errors = []
errors += [f"код {c} бросается в коде, но не описан в docs/API.md" for c in sorted(thrown - rows)]
errors += [f"{c} описан в docs/API.md, но не встречается в коде сервисов" for c in sorted(rows)
           if not re.search(r"\b" + c + r"\b", backend)]
errors += [f"стадия {c} не известна фронту (frontend/src)" for c in sorted(stages)
           if not re.search(r"\b" + c + r"\b", front)]
for e in errors:
    print("✗", e)
print(f"контракты: {len(rows)} строк API.md, {len(thrown)} кодов в коде, {len(stages)} стадий — "
      + ("ok" if not errors else f"{len(errors)} расхождений"))
sys.exit(1 if errors else 0)
