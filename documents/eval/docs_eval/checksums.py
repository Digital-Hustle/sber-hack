"""Контрольные числа документов (алгоритмы из docs/DOC-TYPES.md) — для генерации валидной синтетики."""

from __future__ import annotations

MRZ_WEIGHTS = (7, 3, 1)


def snils_check(digits9: str) -> str:
    """Контрольное число СНИЛС для первых 9 цифр."""
    if len(digits9) != 9 or not digits9.isdigit():
        raise ValueError("СНИЛС: нужно 9 цифр")
    total = sum(int(d) * (9 - i) for i, d in enumerate(digits9))
    if total < 100:
        return f"{total:02d}"
    if total in (100, 101):
        return "00"
    rest = total % 101
    return "00" if rest == 100 else f"{rest:02d}"


def snils_format(digits9: str) -> str:
    """СНИЛС вида NNN-NNN-NNN NN."""
    d = digits9
    return f"{d[0:3]}-{d[3:6]}-{d[6:9]} {snils_check(d)}"


def snils_valid(value: str) -> bool:
    digits = "".join(ch for ch in value if ch.isdigit())
    return len(digits) == 11 and snils_check(digits[:9]) == digits[9:]


_INN_K1 = (7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
_INN_K2 = (3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8)


def inn_complete(digits10: str) -> str:
    """ИНН физлица: 10 цифр → 12 с двумя контрольными разрядами."""
    if len(digits10) != 10 or not digits10.isdigit():
        raise ValueError("ИНН: нужно 10 цифр")
    c1 = sum(int(d) * k for d, k in zip(digits10, _INN_K1, strict=True)) % 11 % 10
    d11 = digits10 + str(c1)
    c2 = sum(int(d) * k for d, k in zip(d11, _INN_K2, strict=True)) % 11 % 10
    return d11 + str(c2)


def inn_valid(value: str) -> bool:
    return len(value) == 12 and value.isdigit() and inn_complete(value[:10]) == value


def _mrz_value(ch: str) -> int:
    if ch.isdigit():
        return int(ch)
    if ch == "<":
        return 0
    if "A" <= ch <= "Z":
        return ord(ch) - ord("A") + 10
    raise ValueError(f"недопустимый символ MRZ: {ch!r}")


def mrz_check(field: str) -> str:
    """Контрольная цифра ICAO 9303 (веса 7-3-1)."""
    return str(sum(_mrz_value(c) * MRZ_WEIGHTS[i % 3] for i, c in enumerate(field)) % 10)


def mrz_td3(
    *, country: str, surname: str, given: str, number: str, nationality: str, birth: str, sex: str, expiry: str
) -> tuple[str, str]:
    """Две строки MRZ паспорта (TD3, по 44 символа). Даты — YYMMDD, имена — латиница."""
    names = f"{surname}<<{given}".upper().replace(" ", "<").replace("-", "<")
    line1 = f"P<{country}{names}"[:44].ljust(44, "<")
    num = number.upper()[:9].ljust(9, "<")
    personal = "<" * 14
    body = (
        num + mrz_check(num) + nationality + birth + mrz_check(birth) + sex + expiry + mrz_check(expiry)
        + personal + mrz_check(personal)
    )
    # Итоговая цифра — по номеру, рождению, сроку и доп. данным вместе с их контрольными цифрами.
    composite = body[0:10] + body[13:20] + body[21:43]
    return line1, body + mrz_check(composite)
