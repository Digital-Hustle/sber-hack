"""Генератор синтетических документов (без реальных людей) и их разметки.

Детерминированный: одинаковый seed → одинаковые записи разметки. Признаки видов взяты из docs/DOC-TYPES.md,
а не из открытого набора (rules.md §1.3). Значения в разметке — уже нормализованные, как их отдаёт docproc
(даты ISO, ФИО в верхнем регистре, номера без пробелов).
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from datetime import date, timedelta
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from .checksums import inn_complete, mrz_td3, snils_format

DOC_TYPES = (
    "PASSPORT_FOREIGN", "PASSPORT_RF", "MIGRATION_CARD", "WORK_PATENT",
    "ARRIVAL_NOTICE", "SNILS", "INN", "MEDICAL",
)
DEGRADATIONS = ("clean", "rotated", "noisy", "soft")

_SURNAMES = ("ИВАНОВ", "СМИРНОВ", "КУЗНЕЦОВ", "ПОПОВ", "ВАСИЛЬЕВ", "ПЕТРОВ", "СОКОЛОВ", "МИХАЙЛОВ", "НОВИКОВ")
_GIVEN = ("АЛЕКСЕЙ", "ДМИТРИЙ", "СЕРГЕЙ", "АНДРЕЙ", "ПАВЕЛ", "ИГОРЬ", "ОЛЕГ", "РОМАН")
_PATRONYMIC = ("АЛЕКСЕЕВИЧ", "ДМИТРИЕВИЧ", "СЕРГЕЕВИЧ", "АНДРЕЕВИЧ", "ПАВЛОВИЧ", "ИГОРЕВИЧ")
_FOREIGN = (
    ("UZB", "УЗБЕКИСТАН", ("KARIMOV", "RAKHIMOV", "YUSUPOV"), ("BOBUR", "JASUR", "SARDOR")),
    ("TJK", "ТАДЖИКИСТАН", ("NAZAROV", "SAIDOV", "RAHMONOV"), ("FIRUZ", "UMED", "DILSHOD")),
    ("KGZ", "КИРГИЗИЯ", ("ASANOV", "TOKTOGULOV", "BEKOV"), ("AIBEK", "NURLAN", "ERLAN")),
)
_CITIES = ("Г. ТВЕРЬ", "Г. КАЗАНЬ", "Г. ОМСК", "Г. ПЕРМЬ", "Г. ТУЛА")
_REGIONS = ("МОСКВА", "МОСКОВСКАЯ ОБЛАСТЬ", "САНКТ-ПЕТЕРБУРГ", "ТАТАРСТАН")
_PROFESSIONS = ("ПОДСОБНЫЙ РАБОЧИЙ", "ВОДИТЕЛЬ", "ПОВАР", "ШТУКАТУР")
_ORGS = ("ГБУЗ ПОЛИКЛИНИКА № 3", "ООО МЕДЦЕНТР ЗДОРОВЬЕ", "ГБУЗ ЦЕНТР ОСВИДЕТЕЛЬСТВОВАНИЯ")
_HOSTS = ("ООО СТРОЙМОНТАЖ", "ООО ВЕКТОР", "АО ЛОГИСТИКА")
_STREETS = ("УЛ. ЛЕНИНА", "УЛ. САДОВАЯ", "ПР. МИРА", "УЛ. ЗЕЛЁНАЯ")

FONT_CANDIDATES = (
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "/usr/share/fonts/liberation-sans/LiberationSans-Regular.ttf",
    "/Library/Fonts/Arial Unicode.ttf",
    "C:/Windows/Fonts/arial.ttf",
)
MONO_CANDIDATES = (
    "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    "/usr/share/fonts/dejavu/DejaVuSansMono.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
    "/usr/share/fonts/liberation-mono/LiberationMono-Regular.ttf",
    "C:/Windows/Fonts/cour.ttf",
)


def find_font(candidates: tuple[str, ...] = FONT_CANDIDATES) -> str | None:
    """Первый установленный шрифт с кириллицей (или None)."""
    return next((p for p in candidates if Path(p).is_file()), None)


@dataclass
class SynthDoc:
    """Один синтетический документ: разметка + строки для отрисовки."""

    id: str
    type: str | None
    degradation: str
    fields: dict[str, str | None]
    lines: list[str] = field(default_factory=list)
    mrz: tuple[str, str] | None = None
    unreadable: bool = False

    def label(self) -> dict:
        return {
            "file": f"{self.id}.jpg",
            "type": self.type,
            "unreadable": self.unreadable,
            "degradation": self.degradation,
            "fields": self.fields,
        }


def _d(x: date) -> str:
    return x.strftime("%d.%m.%Y")


def _digits(rng: random.Random, n: int) -> str:
    return "".join(str(rng.randint(0, 9)) for _ in range(n))


def _date_between(rng: random.Random, start: date, end: date) -> date:
    return start + timedelta(days=rng.randint(0, (end - start).days))


def _person(rng: random.Random, today: date) -> dict:
    birth = _date_between(rng, date(1965, 1, 1), date(2003, 12, 31))
    return {
        "surname": rng.choice(_SURNAMES),
        "given": rng.choice(_GIVEN),
        "patronymic": rng.choice(_PATRONYMIC),
        "birth": birth,
        "today": today,
    }


def _foreign(rng: random.Random) -> tuple[str, str, str, str]:
    code, country, surnames, given = rng.choice(_FOREIGN)
    return code, country, rng.choice(surnames), rng.choice(given)


def _build(doc_type: str, rng: random.Random, today: date) -> tuple[dict, list[str], tuple[str, str] | None]:
    p = _person(rng, today)
    fio = f"{p['surname']} {p['given']} {p['patronymic']}"
    issue = _date_between(rng, max(p["birth"] + timedelta(days=365 * 20), today - timedelta(days=3000)), today)
    if doc_type == "PASSPORT_RF":
        series, number = _digits(rng, 4), _digits(rng, 6)
        dept = f"{_digits(rng, 3)}-{_digits(rng, 3)}"
        place = rng.choice(_CITIES)
        issued_by = f"ГУ МВД РОССИИ ПО {rng.choice(_REGIONS)}"
        fields = {
            "surname": p["surname"], "givenName": p["given"], "patronymic": p["patronymic"], "sex": "M",
            "birthDate": p["birth"].isoformat(), "birthPlace": place, "series": series, "number": number,
            "issueDate": issue.isoformat(), "departmentCode": dept, "issuedBy": issued_by,
        }
        lines = [
            "РОССИЙСКАЯ ФЕДЕРАЦИЯ", "ПАСПОРТ", f"Паспорт выдан: {issued_by}", f"Дата выдачи: {_d(issue)}",
            f"Код подразделения: {dept}", f"Фамилия: {p['surname']}", f"Имя: {p['given']}",
            f"Отчество: {p['patronymic']}", "Пол: МУЖ.", f"Дата рождения: {_d(p['birth'])}",
            f"Место рождения: {place}", f"Серия и номер: {series[:2]} {series[2:]} {number}",
        ]
        return fields, lines, None
    if doc_type == "PASSPORT_FOREIGN":
        code, country, sur, given = _foreign(rng)
        number = f"{rng.choice('ABCFK')}{_digits(rng, 7)}"
        expiry = issue + timedelta(days=365 * 10)
        authority = f"MIA {code}"
        fields = {
            "surname": sur, "givenNames": given, "birthDate": p["birth"].isoformat(), "sex": "M",
            "nationality": code, "documentNumber": number, "issueDate": issue.isoformat(),
            "expiryDate": expiry.isoformat(), "issuingAuthority": authority,
        }
        lines = [
            f"PASSPORT · {country}", f"Surname: {sur}", f"Given names: {given}", f"Nationality: {code}",
            f"Date of birth: {_d(p['birth'])}", "Sex: M", f"Passport No: {number}",
            f"Date of issue: {_d(issue)}", f"Date of expiry: {_d(expiry)}", f"Authority: {authority}",
        ]
        mrz = mrz_td3(country=code, surname=sur, given=given, number=number, nationality=code,
                      birth=p["birth"].strftime("%y%m%d"), sex="M", expiry=expiry.strftime("%y%m%d"))
        return fields, lines, mrz
    if doc_type == "MIGRATION_CARD":
        _, country, _, _ = _foreign(rng)
        series, number = f"{_digits(rng, 2)}{_digits(rng, 2)}", _digits(rng, 7)
        entry = _date_between(rng, today - timedelta(days=80), today)
        until = entry + timedelta(days=90)
        host = rng.choice(_HOSTS)
        fields = {
            "fullName": fio, "citizenship": country, "series": series, "number": number,
            "entryDate": entry.isoformat(), "visitPurpose": "РАБОТА", "stayUntil": until.isoformat(), "host": host,
        }
        lines = [
            "МИГРАЦИОННАЯ КАРТА / MIGRATION CARD", f"Серия {series} № {number}", f"ФИО: {fio}",
            f"Гражданство: {country}", f"Дата въезда: {_d(entry)}", "Цель визита: РАБОТА",
            f"Срок пребывания до: {_d(until)}", f"Принимающая сторона: {host}",
        ]
        return fields, lines, None
    if doc_type == "WORK_PATENT":
        code, country, sur, given = _foreign(rng)
        series, number = f"{_digits(rng, 2)}", _digits(rng, 10)
        passport = f"{rng.choice('ABCFK')}{_digits(rng, 7)}"
        region, prof = rng.choice(_REGIONS), rng.choice(_PROFESSIONS)
        fields = {
            "fullName": fio, "fullNameLatin": f"{sur} {given}", "birthDate": p["birth"].isoformat(),
            "citizenship": country, "series": series, "number": number, "passportNumber": passport,
            "issueDate": issue.isoformat(), "region": region, "profession": prof,
        }
        lines = [
            "ПАТЕНТ", f"Серия {series} № {number}", f"ФИО: {fio}", f"Латиницей: {sur} {given}",
            f"Дата рождения: {_d(p['birth'])}", f"Гражданство: {country}", f"Документ: паспорт {passport}",
            f"Дата выдачи: {_d(issue)}", f"Территория действия: {region}", f"Профессия: {prof}",
        ]
        return fields, lines, None
    if doc_type == "ARRIVAL_NOTICE":
        _, country, _, _ = _foreign(rng)
        passport = f"{rng.choice('ABCFK')}{_digits(rng, 7)}"
        address = f"{rng.choice(_CITIES)}, {rng.choice(_STREETS)}, Д. {rng.randint(1, 99)}, КВ. {rng.randint(1, 200)}"
        until = today + timedelta(days=rng.randint(10, 300))
        host = rng.choice(_HOSTS)
        fields = {
            "fullName": fio, "citizenship": country, "passportNumber": passport, "address": address,
            "stayUntil": until.isoformat(), "host": host,
        }
        lines = [
            "УВЕДОМЛЕНИЕ О ПРИБЫТИИ ИНОСТРАННОГО ГРАЖДАНИНА", "Отрывная часть", f"ФИО: {fio}",
            f"Гражданство: {country}", f"Паспорт: {passport}", f"Адрес пребывания: {address}",
            f"Срок пребывания до: {_d(until)}", f"Принимающая сторона: {host}",
        ]
        return fields, lines, None
    if doc_type == "SNILS":
        snils = snils_format(_digits(rng, 9))
        fields = {"fullName": fio, "snils": snils, "birthDate": p["birth"].isoformat(),
                  "registrationDate": issue.isoformat()}
        lines = [
            "СТРАХОВОЕ СВИДЕТЕЛЬСТВО", "ОБЯЗАТЕЛЬНОГО ПЕНСИОННОГО СТРАХОВАНИЯ", snils, f"ФИО: {fio}",
            f"Дата рождения: {_d(p['birth'])}", f"Дата регистрации: {_d(issue)}",
        ]
        return fields, lines, None
    if doc_type == "INN":
        inn = inn_complete(_digits(rng, 10))
        fields = {"fullName": fio, "inn": inn, "birthDate": p["birth"].isoformat(), "assignedDate": issue.isoformat()}
        lines = [
            "СВИДЕТЕЛЬСТВО", "О ПОСТАНОВКЕ НА УЧЁТ ФИЗИЧЕСКОГО ЛИЦА В НАЛОГОВОМ ОРГАНЕ", f"ФИО: {fio}",
            f"Дата рождения: {_d(p['birth'])}", f"ИНН {inn}", f"Дата постановки на учёт: {_d(issue)}",
        ]
        return fields, lines, None
    if doc_type == "MEDICAL":
        number = _digits(rng, 8)
        med_issue = _date_between(rng, today - timedelta(days=200), today)
        expiry = med_issue + timedelta(days=365)
        org = rng.choice(_ORGS)
        fields = {"fullName": fio, "documentNumber": number, "issueDate": med_issue.isoformat(),
                  "expiryDate": expiry.isoformat(), "organization": org}
        lines = [
            "МЕДИЦИНСКОЕ ЗАКЛЮЧЕНИЕ", "по результатам медицинского освидетельствования", f"№ {number}",
            f"ФИО: {fio}", f"Дата выдачи: {_d(med_issue)}", f"Действительно до: {_d(expiry)}", f"Организация: {org}",
        ]
        return fields, lines, None
    raise ValueError(f"неизвестный вид: {doc_type}")


def generate(
    seed: int = 20261009, per_type: int = 3, unreadable: int = 4, today: date = date(2026, 10, 9)
) -> list[SynthDoc]:
    """Набор синтетики: per_type документов каждого вида (по кругу деградаций) + заведомо непригодные снимки."""
    rng = random.Random(seed)
    docs: list[SynthDoc] = []
    n = 0
    for doc_type in DOC_TYPES:
        for i in range(per_type):
            fields, lines, mrz = _build(doc_type, rng, today)
            n += 1
            docs.append(SynthDoc(f"syn-{n:03d}", doc_type, DEGRADATIONS[i % len(DEGRADATIONS)], fields, lines, mrz))
    for i in range(unreadable):
        doc_type = DOC_TYPES[i % len(DOC_TYPES)]
        fields, lines, mrz = _build(doc_type, rng, today)
        n += 1
        docs.append(SynthDoc(f"syn-{n:03d}", doc_type, "unreadable", fields, lines, mrz, unreadable=True))
    return docs


def render(doc: SynthDoc, font_path: str, mono_path: str | None = None, seed: int = 0) -> Image.Image:
    """Отрисовать документ (≈ A5 при 150 dpi) и применить деградацию."""
    w, h = 1240, 874
    img = Image.new("RGB", (w, h), (246, 244, 236))
    draw = ImageDraw.Draw(img)
    title = ImageFont.truetype(font_path, 40)
    body = ImageFont.truetype(font_path, 30)
    mono = ImageFont.truetype(mono_path or font_path, 30)
    draw.rectangle((20, 20, w - 20, h - 20), outline=(120, 120, 120), width=3)
    y = 50
    for i, line in enumerate(doc.lines):
        font = title if i == 0 else body
        draw.text((60, y), line, font=font, fill=(25, 25, 30))
        y += 58 if i == 0 else 48
    if doc.mrz:
        for k, line in enumerate(doc.mrz):
            draw.text((40, h - 130 + k * 44), line, font=mono, fill=(10, 10, 10))
    rng = random.Random(seed)
    if doc.degradation == "rotated":
        img = img.rotate(rng.choice((-3, -2, 2, 3)), expand=True, fillcolor=(200, 200, 200))
    elif doc.degradation == "noisy":
        px = img.load()
        for _ in range(w * h // 400):
            x, yy = rng.randrange(w), rng.randrange(h)
            v = rng.randint(90, 200)
            px[x, yy] = (v, v, v)
    elif doc.degradation == "soft":
        img = img.filter(ImageFilter.GaussianBlur(1.0))
    elif doc.degradation == "unreadable":
        img = img.resize((w // 8, h // 8)).filter(ImageFilter.GaussianBlur(2)).resize((w, h))
    return img


def write_set(out_dir: Path, labels_path: Path, docs: list[SynthDoc], font_path: str | None) -> int:
    """Записать JPEG (если есть шрифт) и разметку JSONL. Возвращает число отрисованных файлов."""
    import json

    labels_path.parent.mkdir(parents=True, exist_ok=True)
    with labels_path.open("w", encoding="utf-8") as f:
        for d in docs:
            f.write(json.dumps(d.label(), ensure_ascii=False, sort_keys=True) + "\n")
    if not font_path:
        return 0
    out_dir.mkdir(parents=True, exist_ok=True)
    mono = find_font(MONO_CANDIDATES)
    for i, d in enumerate(docs):
        render(d, font_path, mono, seed=i).save(out_dir / f"{d.id}.jpg", quality=85, optimize=True)
    return len(docs)
