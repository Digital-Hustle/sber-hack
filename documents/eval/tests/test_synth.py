import json
from pathlib import Path

import pytest

from docs_eval.checksums import inn_valid, mrz_check, snils_valid
from docs_eval.synth import DOC_TYPES, find_font, generate, render, write_set

HERE = Path(__file__).resolve().parents[1]


def test_deterministic():
    a = [d.label() for d in generate(seed=7)]
    b = [d.label() for d in generate(seed=7)]
    assert a == b
    assert a != [d.label() for d in generate(seed=8)]


def test_every_type_present_and_numbers_valid():
    docs = generate(per_type=2, unreadable=2)
    assert {d.type for d in docs} == set(DOC_TYPES)
    assert sum(d.unreadable for d in docs) == 2
    for d in docs:
        if "snils" in d.fields:
            assert snils_valid(d.fields["snils"])
        if "inn" in d.fields:
            assert inn_valid(d.fields["inn"])
        if d.mrz:
            l2 = d.mrz[1]
            assert l2[43] == mrz_check(l2[0:10] + l2[13:20] + l2[21:43])


def test_dates_are_iso_and_consistent():
    for d in generate():
        f = d.fields
        for k, v in f.items():
            if k.endswith("Date") or k.endswith("Until"):
                assert len(v) == 10 and v[4] == "-" and v[7] == "-"
        if "birthDate" in f and "issueDate" in f:
            assert f["birthDate"] < f["issueDate"]


def test_committed_labels_match_generator(tmp_path):
    """labels/synthetic.jsonl в git собран текущим генератором (python gen_synthetic.py)."""
    out = tmp_path / "labels.jsonl"
    write_set(tmp_path / "img", out, generate(), font_path=None)
    assert out.read_text(encoding="utf-8") == (HERE / "labels" / "synthetic.jsonl").read_text(encoding="utf-8")


def test_committed_images_exist():
    for line in (HERE / "labels" / "synthetic.jsonl").read_text(encoding="utf-8").splitlines():
        assert (HERE / "synthetic" / json.loads(line)["file"]).is_file()


@pytest.mark.skipif(find_font() is None, reason="нет шрифта с кириллицей")
def test_render_sizes():
    doc = generate(per_type=1, unreadable=0)[0]
    img = render(doc, find_font())
    assert img.size == (1240, 874)
