"""Машиночитаемая зона (ICAO 9303): поиск строк, разбор TD1/TD2/TD3, контрольные цифры."""

from __future__ import annotations

import re

from ocr.models import Mrz, MrzCheck

_WEIGHTS = (7, 3, 1)
_ALLOWED = re.compile(r"^[A-Z0-9<]+$")
_FIX_DIGITS = str.maketrans({"O": "0", "Q": "0", "D": "0", "I": "1", "L": "1", "Z": "2", "S": "5", "B": "8", "G": "6"})


def _value(c: str) -> int:
    if c.isdigit():
        return int(c)
    if "A" <= c <= "Z":
        return ord(c) - ord("A") + 10
    return 0


def check_digit(s: str) -> str:
    return str(sum(_value(c) * _WEIGHTS[i % 3] for i, c in enumerate(s)) % 10)


def _digits(s: str) -> str:
    return s.translate(_FIX_DIGITS)


def normalize_line(line: str) -> str:
    s = line.upper().replace(" ", "").replace("«", "<<").replace("‹", "<")
    return re.sub(r"[^A-Z0-9<]", "", s)


def candidate_lines(text: str) -> list[str]:
    out: list[str] = []
    for raw in text.splitlines():
        s = normalize_line(raw)
        if len(s) >= 28 and s.count("<") >= 2 and _ALLOWED.match(s):
            out.append(s)
    return out


def _ok(name: str, field: str, digit: str) -> MrzCheck:
    return MrzCheck(name=name, ok=check_digit(field) == digit)


def _names(s: str) -> tuple[str, str]:
    parts = s.split("<<", 1)
    surname = parts[0].replace("<", " ").strip()
    given = parts[1].replace("<", " ").strip() if len(parts) > 1 else ""
    return surname, re.sub(r"\s+", " ", given)


def parse_td3(l1: str, l2: str) -> Mrz:
    l1, l2 = l1.ljust(44, "<")[:44], l2.ljust(44, "<")[:44]
    number, nd = l2[0:9], _digits(l2[9])
    birth, bd = _digits(l2[13:19]), _digits(l2[19])
    expiry, ed = _digits(l2[21:27]), _digits(l2[27])
    personal, pd = l2[28:42], _digits(l2[42])
    final = _digits(l2[43])
    surname, given = _names(l1[5:44])
    composite = number + nd + birth + bd + expiry + ed + personal + pd
    checks = [
        _ok("documentNumber", number, nd),
        _ok("birthDate", birth, bd),
        _ok("expiryDate", expiry, ed),
        MrzCheck(name="personalNumber", ok=pd == "<" or check_digit(personal) == pd or personal.strip("<") == ""),
        _ok("composite", composite, final),
    ]
    fields = {
        "documentCode": l1[0:2].replace("<", ""),
        "issuingState": l1[2:5].replace("<", ""),
        "surname": surname,
        "givenNames": given,
        "documentNumber": number.replace("<", ""),
        "nationality": l2[10:13].replace("<", ""),
        "birthDate": birth,
        "sex": l2[20].replace("<", ""),
        "expiryDate": expiry,
        "personalNumber": personal.replace("<", ""),
    }
    return Mrz(format="TD3", raw=[l1, l2], fields=fields, checks=checks, valid=all(c.ok for c in checks))


def parse_td2(l1: str, l2: str) -> Mrz:
    l1, l2 = l1.ljust(36, "<")[:36], l2.ljust(36, "<")[:36]
    number, nd = l2[0:9], _digits(l2[9])
    birth, bd = _digits(l2[13:19]), _digits(l2[19])
    expiry, ed = _digits(l2[21:27]), _digits(l2[27])
    optional, final = l2[28:35], _digits(l2[35])
    surname, given = _names(l1[5:36])
    checks = [
        _ok("documentNumber", number, nd),
        _ok("birthDate", birth, bd),
        _ok("expiryDate", expiry, ed),
        _ok("composite", number + nd + birth + bd + expiry + ed + optional, final),
    ]
    fields = {
        "documentCode": l1[0:2].replace("<", ""),
        "issuingState": l1[2:5].replace("<", ""),
        "surname": surname,
        "givenNames": given,
        "documentNumber": number.replace("<", ""),
        "nationality": l2[10:13].replace("<", ""),
        "birthDate": birth,
        "sex": l2[20].replace("<", ""),
        "expiryDate": expiry,
    }
    return Mrz(format="TD2", raw=[l1, l2], fields=fields, checks=checks, valid=all(c.ok for c in checks))


def parse_td1(l1: str, l2: str, l3: str) -> Mrz:
    l1, l2, l3 = (x.ljust(30, "<")[:30] for x in (l1, l2, l3))
    number, nd = l1[5:14], _digits(l1[14])
    birth, bd = _digits(l2[0:6]), _digits(l2[6])
    expiry, ed = _digits(l2[8:14]), _digits(l2[14])
    final = _digits(l2[29])
    surname, given = _names(l3)
    composite = l1[5:30] + l2[0:7] + l2[8:15] + l2[18:29]
    checks = [
        _ok("documentNumber", number, nd),
        _ok("birthDate", birth, bd),
        _ok("expiryDate", expiry, ed),
        _ok("composite", composite, final),
    ]
    fields = {
        "documentCode": l1[0:2].replace("<", ""),
        "issuingState": l1[2:5].replace("<", ""),
        "documentNumber": number.replace("<", ""),
        "birthDate": birth,
        "sex": l2[7].replace("<", ""),
        "expiryDate": expiry,
        "nationality": l2[15:18].replace("<", ""),
        "surname": surname,
        "givenNames": given,
    }
    return Mrz(format="TD1", raw=[l1, l2, l3], fields=fields, checks=checks, valid=all(c.ok for c in checks))


def find(text: str) -> Mrz | None:
    """Лучший разбор MRZ из распознанного текста или None."""
    lines = candidate_lines(text)
    found: list[Mrz] = []
    for i in range(len(lines)):
        a = lines[i]
        b = lines[i + 1] if i + 1 < len(lines) else None
        c = lines[i + 2] if i + 2 < len(lines) else None
        if b is not None and a[0] in "PV" and len(a) >= 40:
            found.append(parse_td3(a, b))
        elif b is not None and c is not None and a[0] in "ACI" and 28 <= len(a) <= 32:
            found.append(parse_td1(a, b, c))
        elif b is not None and a[0] in "ACIPV" and 34 <= len(a) <= 38:
            found.append(parse_td2(a, b))
    if not found:
        return None
    return max(found, key=lambda m: sum(1 for c in m.checks if c.ok))
