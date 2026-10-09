#!/usr/bin/env python3
"""Замер качества разбора (docs/EVAL.md).

Без --api — проверка набора: разметка читается, файлы на месте, номера проходят контрольные суммы (так в CI).
С --api — каждый файл грузится через публичный API как отдельная заявка, после итоговой стадии поля сравниваются
с разметкой; отчёт пишется в reports/<дата>-<набор>.md.

    uv run python run.py --set synthetic
    EVAL_OPEN_DIR=~/datasets/documents-open EVAL_OPERATOR_TOKEN=... uv run python run.py --set open --api https://<host>
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import sys
from datetime import date
from pathlib import Path

from docs_eval.checksums import inn_valid, snils_valid
from docs_eval.client import Api, ApiError, PasswordToken
from docs_eval.metrics import Outcome, evaluate, to_markdown
from docs_eval.synth import DOC_TYPES

HERE = Path(__file__).resolve().parent


def load_labels(path: Path) -> list[dict]:
    rows = []
    with path.open(encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            if "file" not in row or "fields" not in row:
                raise ValueError(f"{path}:{n}: нужны поля file и fields")
            rows.append(row)
    return rows


def dataset(name: str) -> tuple[Path, Path]:
    """Каталог файлов и файл разметки набора. Открытый набор и его разметка — вне git (EVAL_OPEN_DIR)."""
    if name == "synthetic":
        return HERE / "synthetic", HERE / "labels" / "synthetic.jsonl"
    root = os.environ.get("EVAL_OPEN_DIR") if name == "open" else os.environ.get("EVAL_OWN_DIR")
    if not root:
        raise SystemExit(f"набор {name}: задайте {'EVAL_OPEN_DIR' if name == 'open' else 'EVAL_OWN_DIR'}")
    base = Path(root).expanduser()
    return base, base / "labels.jsonl"


def validate(rows: list[dict], files_dir: Path) -> list[str]:
    """Проблемы набора: неизвестный вид, нет файла, номер не проходит контрольную сумму."""
    problems = []
    for r in rows:
        if r.get("type") not in (*DOC_TYPES, None):
            problems.append(f"{r['file']}: неизвестный вид {r.get('type')}")
        if not (files_dir / r["file"]).is_file():
            problems.append(f"{r['file']}: файла нет в {files_dir}")
        f = r["fields"]
        if f.get("snils") and not snils_valid(f["snils"]):
            problems.append(f"{r['file']}: СНИЛС не проходит контрольное число")
        if f.get("inn") and not inn_valid(f["inn"]):
            problems.append(f"{r['file']}: ИНН не проходит контрольные разряды")
    return problems


def measure(rows: list[dict], files_dir: Path, api: Api, deadline_s: float) -> list[Outcome]:
    outcomes = []
    for i, r in enumerate(rows, 1):
        path = files_dir / r["file"]
        o = Outcome(file=r["file"], expected_type=r.get("type"), expected_fields=r["fields"],
                    unreadable=bool(r.get("unreadable")), stage=None, predicted_type=None,
                    category=r.get("degradation") or r.get("category") or "")
        try:
            mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            res = api.process(path.name, path.read_bytes(), mime, deadline_s)
            o.stage, o.predicted_type, o.predicted_fields, o.seconds = res.stage, res.type, res.fields, res.seconds
        except (ApiError, OSError) as e:
            o.error = str(e)
        print(f"[{i}/{len(rows)}] {r['file']}: {o.stage or 'ошибка'} {o.predicted_type or ''}", file=sys.stderr)
        outcomes.append(o)
    return outcomes


def summary(r, label: str) -> dict:
    """Короткая сводка для таблицы сравнения моделей (LLM.md, FR-42)."""
    def num(x: float) -> float | None:
        return None if x != x else round(x, 4)  # NaN → null
    return {"label": label, "total": r.total, "readable": r.readable, "typeAccuracy": num(r.type_accuracy),
            "unknownRate": num(r.unknown_rate), "reviewRate": num(r.review_rate), "exactMatch": num(r.exact_match),
            "unreadableCaught": num(r.unreadable_caught), "falseReject": num(r.false_reject),
            "p50": num(r.p50), "p95": num(r.p95), "errors": len(r.errors)}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--set", default="synthetic", choices=["synthetic", "open", "own"])
    ap.add_argument("--api", help="базовый URL стенда, например https://documents.example")
    ap.add_argument("--limit", type=int, default=0, help="взять первые N документов")
    ap.add_argument("--deadline", type=float, default=180.0, help="сколько ждать итоговой стадии, с")
    ap.add_argument("--insecure", action="store_true", help="не проверять TLS (локальный стенд с внутренним CA)")
    ap.add_argument("--max-errors", type=float, default=1.0,
                    help="доля сбоев прогона, выше которой код выхода 1 (smoke: 0)")
    ap.add_argument("--out", type=Path, help="куда писать отчёт (по умолчанию reports/<дата>-<набор>.md)")
    ap.add_argument("--json", type=Path, help="дополнительно сводка в JSON (сравнение моделей, bench)")
    ap.add_argument("--label", default="", help="подпись прогона в отчёте (например, модель)")
    args = ap.parse_args(argv)

    files_dir, labels = dataset(args.set)
    rows = load_labels(labels)
    if args.limit:
        rows = rows[: args.limit]
    problems = validate(rows, files_dir)
    for p in problems:
        print(f"✖ {p}", file=sys.stderr)
    if not args.api:
        by_type: dict[str, int] = {}
        for r in rows:
            by_type[str(r.get("type"))] = by_type.get(str(r.get("type")), 0) + 1
        print(f"набор {args.set}: {len(rows)} документов, {by_type}; проблем: {len(problems)}")
        return 1 if problems else 0
    if problems:
        return 1

    token: str | PasswordToken | None = os.environ.get("EVAL_OPERATOR_TOKEN")
    if not token and os.environ.get("EVAL_TOKEN_URL"):
        token = PasswordToken(os.environ["EVAL_TOKEN_URL"], os.environ.get("EVAL_CLIENT_ID", "documents-eval"),
                              os.environ.get("EVAL_USERNAME", "operator"), os.environ["EVAL_TOTP_SECRET"],
                              insecure=args.insecure)
    api = Api(args.api, token, insecure=args.insecure)
    report = evaluate(measure(rows, files_dir, api, args.deadline))
    title = " · ".join(x for x in ("Eval", args.set, args.label, date.today().isoformat()) if x)
    md = to_markdown(report, title,
                     {"Стенд": args.api, "Документов": str(len(rows)),
                      "Поля": "меряются (токен оператора)" if token else "не меряются — нет EVAL_OPERATOR_TOKEN"})
    out = args.out or HERE / "reports" / f"{date.today().isoformat()}-{args.set}.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(md, encoding="utf-8")
    print(md)
    if args.json:
        args.json.write_text(json.dumps(summary(report, args.label), ensure_ascii=False, indent=1), encoding="utf-8")
    error_share = len(report.errors) / len(rows) if rows else 0.0
    return 1 if error_share > args.max_errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
