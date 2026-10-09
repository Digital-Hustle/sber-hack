#!/usr/bin/env python3
"""Сводная таблица замера моделей (FR-42, X-04) из каталогов bench-*/ (артефакты documents-llm-bench).

В каждом каталоге: summary.json (eval/run.py --json), meta.json (модель, vision, RAM, раннер, время прогрева).
    python3 scripts/bench-summary.py <каталог с bench-*> > bench.md
"""
import json
import sys
from pathlib import Path


def pct(x):
    return "—" if x is None else f"{x * 100:.0f}%"


def sec(x):
    return "—" if x is None else f"{x:.0f} с"


def main(root: str) -> int:
    rows = []
    for d in sorted(Path(root).glob("bench-*")):
        try:
            s = json.loads((d / "summary.json").read_text(encoding="utf-8"))
        except FileNotFoundError:
            s = {}
        m = json.loads((d / "meta.json").read_text(encoding="utf-8")) if (d / "meta.json").exists() else {}
        rows.append((m, s))
    print("| Модель (текст + vision) | Пик RAM Ollama | Время на документ p50 / p95 | Тип: accuracy | Поля: exact match"
          " | Непригодные отбракованы | На проверку | Сбоев |")
    print("|---|---|---|---|---|---|---|---|")
    for m, s in rows:
        name = m.get("model", "?") + (f" + {m['vision']}" if m.get("vision") else "")
        print(f"| `{name}` | {m.get('peakRam', '—')} | {sec(s.get('p50'))} / {sec(s.get('p95'))} | "
              f"{pct(s.get('typeAccuracy'))} | {pct(s.get('exactMatch'))} | {pct(s.get('unreadableCaught'))} | "
              f"{pct(s.get('reviewRate'))} | {s.get('errors', 'нет отчёта')} |")
    if rows:
        print(f"\nРаннер: {rows[0][0].get('runner', '?')}; набор: синтетика `eval/synthetic` ({rows[0][1].get('total', '?')} файлов),"
              " по одному документу за раз, без подгонки (rules.md §1).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1] if len(sys.argv) > 1 else "."))
