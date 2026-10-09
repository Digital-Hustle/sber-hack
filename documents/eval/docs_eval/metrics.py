"""Метрики качества разбора (docs/EVAL.md §Метрики). Чистые функции — без сети и файлов."""

from __future__ import annotations

import math
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime

UNREADABLE_STAGES = frozenset({"UNREADABLE"})
REVIEW_STAGES = frozenset({"NEEDS_REVIEW"})
DONE_STAGES = frozenset({"PARSED", "NEEDS_REVIEW", "UNREADABLE", "FAILED", "REJECTED"})
CONFIDENCE_BUCKETS = ((0.0, 0.5), (0.5, 0.7), (0.7, 0.9), (0.9, 1.01))

_DATE_FORMATS = ("%Y-%m-%d", "%d.%m.%Y", "%d/%m/%Y", "%d-%m-%Y", "%Y.%m.%d")
_DATE_FIELDS = re.compile(r"(Date|Until)$")
_NUMBER_FIELDS = frozenset({
    "series", "number", "documentNumber", "passportNumber", "snils", "inn", "departmentCode",
})


def _norm_text(value: str) -> str:
    v = value.upper().replace("Ё", "Е")
    v = re.sub(r"[\"'«»“”.,;:№#()]", " ", v)
    return re.sub(r"\s+", " ", v).strip()


def normalize(name: str, value: str | None) -> str | None:
    """Нормализация для сравнения: даты → ISO, номера → только буквы/цифры, текст → верхний регистр без пунктуации."""
    if value is None:
        return None
    v = str(value).strip()
    if not v:
        return None
    if _DATE_FIELDS.search(name):
        for fmt in _DATE_FORMATS:
            try:
                return datetime.strptime(v, fmt).date().isoformat()
            except ValueError:
                continue
        return v
    if name in _NUMBER_FIELDS:
        return re.sub(r"[^0-9A-ZА-Я]", "", v.upper())
    return _norm_text(v)


@dataclass
class Outcome:
    """Итог одного документа: разметка + что вернула система."""

    file: str
    expected_type: str | None
    expected_fields: dict[str, str | None]
    unreadable: bool
    stage: str | None
    predicted_type: str | None
    predicted_fields: dict[str, tuple[str | None, float | None]] | None = field(default_factory=dict)
    seconds: float | None = None
    category: str = ""
    error: str | None = None


@dataclass
class FieldStat:
    labeled: int = 0
    filled: int = 0
    correct: int = 0
    hallucinated: int = 0

    @property
    def precision(self) -> float:
        return self.correct / self.filled if self.filled else math.nan

    @property
    def recall(self) -> float:
        return self.correct / self.labeled if self.labeled else math.nan


@dataclass
class Report:
    total: int
    readable: int
    type_accuracy: float
    unknown_rate: float
    review_rate: float
    confusion: dict[tuple[str, str], int]
    fields: dict[str, FieldStat]
    exact_match: float
    unreadable_caught: float
    false_reject: float
    p50: float
    p95: float
    calibration: list[tuple[str, int, float]]
    by_category: dict[str, tuple[int, float]]
    errors: list[str]


def percentile(values: list[float], q: float) -> float:
    """Перцентиль с линейной интерполяцией (q в 0..100)."""
    if not values:
        return math.nan
    s = sorted(values)
    k = (len(s) - 1) * q / 100
    lo, hi = math.floor(k), math.ceil(k)
    return s[lo] if lo == hi else s[lo] + (s[hi] - s[lo]) * (k - lo)


def _ratio(a: int, b: int) -> float:
    return a / b if b else math.nan


def evaluate(outcomes: list[Outcome]) -> Report:
    """Свести исходы в отчёт. Тип и поля считаются только по пригодным документам."""
    readable = [o for o in outcomes if not o.unreadable]
    unreadable = [o for o in outcomes if o.unreadable]
    confusion: Counter[tuple[str, str]] = Counter()
    fields: dict[str, FieldStat] = defaultdict(FieldStat)
    buckets: dict[int, list[bool]] = defaultdict(list)
    cat_total: Counter[str] = Counter()
    cat_ok: Counter[str] = Counter()
    type_ok = unknown = review = all_fields = all_correct = 0
    for o in readable:
        pred = o.predicted_type or "UNKNOWN"
        confusion[(o.expected_type or "UNKNOWN", pred)] += 1
        ok_type = pred == o.expected_type
        type_ok += ok_type
        unknown += pred == "UNKNOWN"
        review += o.stage in REVIEW_STAGES
        doc_ok = ok_type
        if o.predicted_fields is None:  # поля не меряли (нет токена оператора) — только тип
            cat = o.category or "—"
            cat_total[cat] += 1
            cat_ok[cat] += doc_ok
            continue
        for name, exp_raw in o.expected_fields.items():
            exp = normalize(name, exp_raw)
            got_raw, conf = o.predicted_fields.get(name, (None, None)) if ok_type else (None, None)
            got = normalize(name, got_raw)
            st = fields[f"{o.expected_type}.{name}"]
            if exp is not None:
                st.labeled += 1
                all_fields += 1
            if got is not None:
                st.filled += 1
                if exp is None:
                    st.hallucinated += 1
            correct = exp is not None and got == exp
            st.correct += correct
            all_correct += correct
            doc_ok = doc_ok and (exp == got)
            if got is not None and conf is not None:
                idx = next(i for i, (lo, hi) in enumerate(CONFIDENCE_BUCKETS) if lo <= conf < hi)
                buckets[idx].append(got == exp)
        cat = o.category or "—"
        cat_total[cat] += 1
        cat_ok[cat] += doc_ok
    calibration = []
    for i, (lo, hi) in enumerate(CONFIDENCE_BUCKETS):
        hits = buckets.get(i, [])
        calibration.append((f"{lo:.1f}–{min(hi, 1.0):.1f}", len(hits), _ratio(sum(hits), len(hits))))
    caught = sum(o.stage in UNREADABLE_STAGES for o in unreadable)
    false_reject = sum(o.stage in UNREADABLE_STAGES for o in readable)
    secs = [o.seconds for o in outcomes if o.seconds is not None]
    return Report(
        total=len(outcomes),
        readable=len(readable),
        type_accuracy=_ratio(type_ok, len(readable)),
        unknown_rate=_ratio(unknown, len(readable)),
        review_rate=_ratio(review, len(readable)),
        confusion=dict(confusion),
        fields=dict(sorted(fields.items())),
        exact_match=_ratio(all_correct, all_fields),
        unreadable_caught=_ratio(caught, len(unreadable)),
        false_reject=_ratio(false_reject, len(readable)),
        p50=percentile(secs, 50),
        p95=percentile(secs, 95),
        calibration=calibration,
        by_category={k: (cat_total[k], _ratio(cat_ok[k], cat_total[k])) for k in sorted(cat_total)},
        errors=[f"{o.file}: {o.error}" for o in outcomes if o.error],
    )


def _pct(x: float) -> str:
    return "—" if math.isnan(x) else f"{x * 100:.1f}%"


def _sec(x: float) -> str:
    return "—" if math.isnan(x) else f"{x:.1f} с"


def to_markdown(r: Report, title: str, meta: dict[str, str] | None = None) -> str:
    """Отчёт в markdown (eval/reports/<date>.md)."""
    out = [f"# {title}", ""]
    for k, v in (meta or {}).items():
        out.append(f"- {k}: {v}")
    out += [
        "",
        "## Итог",
        "| Метрика | Значение |",
        "|---|---|",
        f"| Документов (пригодных) | {r.total} ({r.readable}) |",
        f"| Тип: accuracy | {_pct(r.type_accuracy)} |",
        f"| Тип: UNKNOWN | {_pct(r.unknown_rate)} |",
        f"| Ушло на проверку (NEEDS_REVIEW) | {_pct(r.review_rate)} |",
        f"| Поля: exact match | {_pct(r.exact_match)} |",
        f"| Непригодные отбракованы | {_pct(r.unreadable_caught)} |",
        f"| Ложные отказы | {_pct(r.false_reject)} |",
        f"| Время p50 / p95 | {_sec(r.p50)} / {_sec(r.p95)} |",
        "",
        "## Поля",
        "| Поле | Размечено | Заполнено | Верно | Precision | Recall | Выдумано |",
        "|---|---|---|---|---|---|---|",
    ]
    for name, s in r.fields.items():
        out.append(
            f"| {name} | {s.labeled} | {s.filled} | {s.correct} | {_pct(s.precision)} | {_pct(s.recall)} "
            f"| {s.hallucinated} |"
        )
    out += ["", "## Калибровка уверенности", "| Корзина | Полей | Верно |", "|---|---|---|"]
    out += [f"| {b} | {n} | {_pct(acc)} |" for b, n, acc in r.calibration]
    out += ["", "## Категории (документ целиком верен)", "| Категория | Документов | Верно |", "|---|---|---|"]
    out += [f"| {k} | {n} | {_pct(acc)} |" for k, (n, acc) in r.by_category.items()]
    mistakes = {k: v for k, v in r.confusion.items() if k[0] != k[1]}
    out += ["", "## Ошибки типа (ожидалось → получено)"]
    out += [f"- {a} → {b}: {n}" for (a, b), n in sorted(mistakes.items())] or ["- нет"]
    if r.errors:
        out += ["", "## Сбои прогона", *[f"- {e}" for e in r.errors]]
    return "\n".join(out) + "\n"
