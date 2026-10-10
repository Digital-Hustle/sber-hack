import datetime as dt
import json
import random
from decimal import Decimal
from pathlib import Path

import pdfplumber

from synthetic import gen
from synthetic import requisites as rq
from synthetic.words import rubles

DAY = dt.date(2026, 10, 9)


def test_requisites_checksums() -> None:
    rnd = random.Random(3)
    for _ in range(50):
        assert rq.inn_valid(rq.inn10(rnd))
        assert rq.ogrn_valid(rq.ogrn13(rnd, 2018))
        b = rq.bik(rnd)
        assert len(b) == 9
        assert rq.account_valid(b, rq.account(b, rnd))
    assert rq.inn_valid("7707083893")
    assert not rq.inn_valid("7707083894")


def test_rubles_in_words() -> None:
    assert rubles(Decimal("1730175.86")) == "Один миллион семьсот тридцать тысяч сто семьдесят пять рублей 86 копеек"
    assert rubles(Decimal("21.01")) == "Двадцать один рубль 01 копейка"
    assert rubles(Decimal("2002.00")) == "Две тысячи два рубля 00 копеек"


def test_package_has_case_types_and_consistent_sums(tmp_path: Path) -> None:
    labels = gen.write(tmp_path, 11, DAY, merged=True, defect=None)
    types = sorted(s["type"] for segs in labels["files"].values() for s in segs)
    assert types == sorted(
        [
            "APPLICATION",
            "PD_CONSENT",
            "PRESENTATION",
            "COST_JUSTIFICATION",
            "BANK_STATEMENT",
            "GUARANTEE_LETTER",
            "CERTIFICATE",
            "PROPERTY_DOC",
            "GIMS_DECLARATION",
        ]
    )
    assert any(len(segs) == 3 for segs in labels["files"].values())
    assert all(name.startswith("doc-") for name in labels["files"])
    fields = {s["type"]: s["fields"] for segs in labels["files"].values() for s in segs}
    cost = fields["COST_JUSTIFICATION"]
    total, own = Decimal(cost["totalCost"]), Decimal(cost["ownFunds"])
    assert own * 2 >= total
    assert Decimal(fields["APPLICATION"]["requestedAmount"]) == total - own == Decimal(cost["requestedAmount"])
    saved = json.loads((tmp_path / "labels.json").read_text(encoding="utf-8"))
    assert set(saved["expectedRules"].values()) == {"PASSED"}


def test_defects_flip_expected_rules(tmp_path: Path) -> None:
    share = gen.write(tmp_path / "share", 5, DAY, merged=False, defect="share")
    cost = next(s["fields"] for segs in share["files"].values() for s in segs if s["type"] == "COST_JUSTIFICATION")
    assert Decimal(cost["ownFunds"]) * 2 < Decimal(cost["totalCost"])
    assert share["expectedRules"]["OWN_FUNDS_SHARE"] == "FAILED"
    assert (
        gen.write(tmp_path / "m", 5, DAY, merged=False, defect="mismatch")["expectedRules"]["REQUEST_MATCH"] == "FAILED"
    )


def test_pdf_has_text_layer_and_neutral_name(tmp_path: Path) -> None:
    labels = gen.write(tmp_path, 5, DAY, merged=False, defect=None)
    name = next(n for n, segs in labels["files"].items() if segs[0]["type"] == "COST_JUSTIFICATION")
    with pdfplumber.open(tmp_path / name) as pdf:
        text = pdf.pages[0].extract_text()
    assert "ОБОСНОВАНИЕ РАЗМЕРА ЗАТРАТ" in text
    assert "COST" not in name
