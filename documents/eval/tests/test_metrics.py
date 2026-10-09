import math

from docs_eval.metrics import Outcome, evaluate, normalize, percentile, to_markdown


def test_normalize_dates_numbers_names():
    assert normalize("birthDate", "01.02.1990") == "1990-02-01"
    assert normalize("stayUntil", "2027-01-05") == "2027-01-05"
    assert normalize("snils", "112-233-445 95") == "11223344595"
    assert normalize("fullName", "  Пётр   Иванов ") == "ПЕТР ИВАНОВ"
    assert normalize("fullName", "") is None
    assert normalize("issuedBy", None) is None


def test_percentile():
    assert percentile([1, 2, 3, 4], 50) == 2.5
    assert percentile([5], 95) == 5
    assert math.isnan(percentile([], 50))


def _o(**kw):
    base = dict(file="f", expected_type="SNILS", expected_fields={"snils": "112-233-445 95", "fullName": "А Б"},
                unreadable=False, stage="PARSED", predicted_type="SNILS",
                predicted_fields={"snils": ("11223344595", 0.95), "fullName": ("а  б", 0.6)}, seconds=2.0)
    base.update(kw)
    return Outcome(**base)


def test_evaluate_perfect_document():
    r = evaluate([_o()])
    assert r.type_accuracy == 1.0
    assert r.exact_match == 1.0
    assert r.fields["SNILS.snils"].precision == 1.0
    assert r.by_category["—"] == (1, 1.0)


def test_wrong_type_gives_no_field_credit():
    r = evaluate([_o(predicted_type="INN")])
    assert r.type_accuracy == 0.0
    assert r.exact_match == 0.0
    assert r.confusion[("SNILS", "INN")] == 1


def test_hallucinated_and_recall():
    r = evaluate([_o(expected_fields={"snils": "112-233-445 95", "fullName": None},
                     predicted_fields={"snils": (None, None), "fullName": ("КТО-ТО", 0.4)})])
    assert r.fields["SNILS.fullName"].hallucinated == 1
    assert r.fields["SNILS.snils"].recall == 0.0


def test_unreadable_and_false_reject():
    r = evaluate([_o(unreadable=True, stage="UNREADABLE"), _o(stage="UNREADABLE")])
    assert r.unreadable_caught == 1.0
    assert r.false_reject == 1.0
    assert r.readable == 1


def test_calibration_buckets_and_markdown():
    r = evaluate([_o()])
    hi = next(c for c in r.calibration if c[0].startswith("0.9"))
    assert hi[1] == 1 and hi[2] == 1.0
    md = to_markdown(r, "t", {"Стенд": "x"})
    assert "| Тип: accuracy | 100.0% |" in md
    assert "SNILS.snils" in md


def test_fields_not_measured_without_token():
    r = evaluate([_o(predicted_fields=None)])
    assert r.type_accuracy == 1.0
    assert r.fields == {}
    assert math.isnan(r.exact_match)
