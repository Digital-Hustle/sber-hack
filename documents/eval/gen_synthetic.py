#!/usr/bin/env python3
"""Пересобрать синтетику: synthetic/*.jpg + labels/synthetic.jsonl. Детерминированно (seed)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from docs_eval.synth import find_font, generate, write_set

HERE = Path(__file__).resolve().parent


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--seed", type=int, default=20261009)
    ap.add_argument("--per-type", type=int, default=3)
    ap.add_argument("--unreadable", type=int, default=4)
    args = ap.parse_args(argv)
    font = find_font()
    if not font:
        print("нет шрифта с кириллицей (DejaVu/Liberation) — пишу только разметку", file=sys.stderr)
    docs = generate(seed=args.seed, per_type=args.per_type, unreadable=args.unreadable)
    n = write_set(HERE / "synthetic", HERE / "labels" / "synthetic.jsonl", docs, font)
    print(f"разметка: {len(docs)} записей, картинок: {n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
