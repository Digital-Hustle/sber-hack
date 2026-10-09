#!/usr/bin/env python3
"""Наполнить стенд правдоподобными заявками через публичный API соискателя (тот же путь, что у человека).

Для каждого «соискателя» — согласованный набор документов (одно ФИО во всех), анкета, загрузка по пунктам,
отправка оператору (часть заявок остаётся черновиком). Разбор делает модель стенда — как для живых файлов.

    python3 eval/seed_stand.py --base https://documents.example.ru --count 8
"""

from __future__ import annotations

import argparse
import io
import json
import random
import sys
import urllib.request
import uuid
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from docs_eval import synth  # noqa: E402
from docs_eval.client import Api  # noqa: E402

# Кириллица ↔ латиница — чтобы паспорт и патент сходились при сверке ФИО.
PEOPLE_FOREIGN = [
    ("КАРИМОВ", "САРДОР", "БАХТИЁРОВИЧ", "KARIMOV", "SARDOR", "UZB", "УЗБЕКИСТАН"),
    ("НАЗАРОВ", "ФИРУЗ", "УМЕДОВИЧ", "NAZAROV", "FIRUZ", "TJK", "ТАДЖИКИСТАН"),
    ("ЮСУПОВ", "ЖАСУР", "АЛИШЕРОВИЧ", "YUSUPOV", "JASUR", "UZB", "УЗБЕКИСТАН"),
    ("РАХМОНОВ", "ДИЛШОД", "САИДОВИЧ", "RAHMONOV", "DILSHOD", "TJK", "ТАДЖИКИСТАН"),
    ("АСАНОВ", "НУРЛАН", "ЭРКИНОВИЧ", "ASANOV", "NURLAN", "KGZ", "КИРГИЗИЯ"),
]
PEOPLE_RF = [
    ("СОКОЛОВ", "ПАВЕЛ", "ИГОРЕВИЧ"), ("КУЗНЕЦОВА", "АННА", "СЕРГЕЕВНА"), ("ПЕТРОВ", "ОЛЕГ", "АНДРЕЕВИЧ"),
]
DEGR = ("clean", "clean", "rotated", "noisy", "soft")


def person_patch(p: tuple, rng: random.Random) -> None:
    birth = synth._date_between(rng, date(1975, 1, 1), date(2003, 12, 31))
    sur, given, patr = p[0], p[1], p[2]
    synth._person = lambda _rng, today: {"surname": sur, "given": given, "patronymic": patr, "birth": birth, "today": today}
    if len(p) > 3:
        synth._foreign = lambda _rng: (p[5], p[6], p[3], p[4])


def jpeg(doc: synth.SynthDoc, font: str, mono: str | None, seed: int) -> bytes:
    buf = io.BytesIO()
    synth.render(doc, font, mono, seed=seed).save(buf, "JPEG", quality=85)
    return buf.getvalue()


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--base", required=True)
    ap.add_argument("--count", type=int, default=8)
    ap.add_argument("--drafts", type=int, default=2, help="сколько заявок оставить черновиком")
    ap.add_argument("--seed", type=int, default=20261010)
    a = ap.parse_args(argv)
    font = synth.find_font()
    if not font:
        print("нет шрифта с кириллицей", file=sys.stderr)
        return 1
    mono = synth.find_font(synth.MONO_CANDIDATES)
    rng = random.Random(a.seed)
    api = Api(a.base, None, timeout=60)
    people = [(p, "VISA_FREE") for p in PEOPLE_FOREIGN] + [(p, "RF") for p in PEOPLE_RF]
    today = date.today()
    for i in range(a.count):
        p, cz = people[i % len(people)]
        person_patch(p, random.Random(a.seed + i))
        app_id, key = api.create_application()
        answers = {"citizenship": cz}
        if cz == "RF":
            answers["medicalRequired"] = rng.random() < 0.5
        else:
            answers.update(hasInn=rng.random() < 0.6, hasSnils=rng.random() < 0.4)
        rs = api._call("PUT", f"/intake/api/v1/applicant/applications/{app_id}/profile", auth=f"Applicant {key}",
                       body=json.dumps({"answers": answers}).encode(), ctype="application/json")
        req = [r["type"] for r in rs.get("requiredDocuments") or [] if r.get("type") in synth.DOC_TYPES]
        # иногда соискатель забывает один документ — оператор увидит «не хватает»
        skip = rng.choice(req) if len(req) > 2 and rng.random() < 0.3 else None
        for n, t in enumerate(req):
            if t == skip:
                continue
            fields, lines, mrz = synth._build(t, rng, today)
            d = synth.SynthDoc(f"seed-{i}-{n}", t, rng.choice(DEGR), fields, lines, mrz)
            data = jpeg(d, font, mono, seed=i * 10 + n)
            boundary = uuid.uuid4().hex
            body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{t.lower()}.jpg\"\r\n"
                    f"Content-Type: image/jpeg\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
            api._call("POST", f"/intake/api/v1/applicant/applications/{app_id}/documents?slotType={t}",
                      auth=f"Applicant {key}", body=body, ctype=f"multipart/form-data; boundary={boundary}",
                      headers={"Idempotency-Key": uuid.uuid4().hex})
        status = "DRAFT"
        if i >= a.drafts:
            try:
                status = api._call("POST", f"/intake/api/v1/applicant/applications/{app_id}/submit", auth=f"Applicant {key}").get("status", "?")
            except Exception as e:  # noqa: BLE001 — неполный комплект может не отправиться, это тоже правдоподобно
                status = f"не отправлена: {e}"[:120]
        print(f"{i + 1}/{a.count} {p[0]} {cz}: {len(req) - (1 if skip else 0)}/{len(req)} док., {status}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
