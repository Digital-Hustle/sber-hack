import json

import run
from docs_eval.client import ApiError, DocResult


def test_synthetic_set_is_valid():
    files, labels = run.dataset("synthetic")
    rows = run.load_labels(labels)
    assert rows
    assert run.validate(rows, files) == []
    assert run.main(["--set", "synthetic"]) == 0


def test_validate_reports_problems(tmp_path):
    rows = [{"file": "x.png", "type": "BOGUS", "fields": {"snils": "112-233-445 96", "inn": "500100732258"}}]
    problems = run.validate(rows, tmp_path)
    assert len(problems) == 4


class FakeApi:
    def __init__(self):
        self.calls = 0

    def process(self, name, data, mime, deadline_s):
        self.calls += 1
        if self.calls == 2:
            raise ApiError("сбой")
        return DocResult(stage="PARSED", type="INN", fields={"inn": ("500100732259", 0.97)}, seconds=1.5)


def test_measure_counts_errors(tmp_path):
    (tmp_path / "a.png").write_bytes(b"x")
    (tmp_path / "b.png").write_bytes(b"x")
    rows = [{"file": n, "type": "INN", "fields": {"inn": "500100732259"}} for n in ("a.png", "b.png")]
    outcomes = run.measure(rows, tmp_path, FakeApi(), 1)
    assert outcomes[0].predicted_fields["inn"][0] == "500100732259"
    assert outcomes[1].error == "сбой"
    labels = tmp_path / "l.jsonl"
    labels.write_text("\n".join(json.dumps(r) for r in rows), encoding="utf-8")
    assert len(run.load_labels(labels)) == 2


def test_max_errors_exit_code(tmp_path, monkeypatch):
    class Boom:
        def __init__(self, *a, **kw):
            pass

        def process(self, *a, **kw):
            raise ApiError("нет стенда")

    monkeypatch.setattr(run, "Api", Boom)
    out = tmp_path / "r.md"
    args = ["--set", "synthetic", "--api", "https://x", "--limit", "1", "--out", str(out)]
    assert run.main([*args, "--max-errors", "0"]) == 1
    assert run.main(args) == 0
    assert "Сбои прогона" in out.read_text(encoding="utf-8")
