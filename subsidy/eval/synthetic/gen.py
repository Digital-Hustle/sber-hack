"""Генератор синтетического пакета: 9 видов документов кейса 3 вымышленной организации, PDF с текстовым слоем.

python -m synthetic.gen --out /tmp/pkg --seed 7 [--date 2026-10-09] [--merged] [--defect share|mismatch|expired|inn]
Имена файлов нейтральные (doc-XXXX.pdf), порядок перемешан: вид определяется только по содержимому.
Разметка — labels.json: {file: [{fromPage, toPage, type, fields}]} и ожидаемые статусы правил.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import random
from dataclasses import dataclass, field
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path
from typing import Any

from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen.canvas import Canvas

from synthetic import requisites as rq
from synthetic.words import rubles

FONTS = [
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "/usr/share/fonts/liberation-sans/LiberationSans-Regular.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
CENT = Decimal("0.01")
NAMES = ["Вектор", "Северный ветер", "Градиент", "Кедр", "Орбита", "Полярис", "Сфера", "Магистраль"]
GOODS = [
    ("Станок токарный с ЧПУ", "шт"),
    ("Фрезерный обрабатывающий центр", "шт"),
    ("Пусконаладочные работы", "усл"),
    ("Доставка оборудования", "усл"),
    ("Комплект оснастки", "компл"),
    ("Обучение операторов", "усл"),
]
HEADS = ["Смирнов Олег Петрович", "Кузнецова Ирина Викторовна", "Волков Денис Андреевич", "Орлова Мария Сергеевна"]


def money(v: Decimal) -> str:
    s = f"{v.quantize(CENT):,.2f}"
    return s.replace(",", " ").replace(".", ",")


def font() -> str:
    for p in FONTS:
        if Path(p).exists():
            pdfmetrics.registerFont(TTFont("Body", p))
            return "Body"
    raise SystemExit("нет TTF-шрифта с кириллицей (fonts-liberation)")


@dataclass
class Org:
    name: str
    inn: str
    kpp: str
    ogrn: str
    bik: str
    account: str
    head: str
    address: str


@dataclass
class Doc:
    type: str
    title: str
    lines: list[str]
    fields: dict[str, Any]
    table: list[list[str]] = field(default_factory=list)
    after: list[str] = field(default_factory=list)


def org(rnd: random.Random, kind: str) -> Org:
    inn = rq.inn10(rnd)
    b = rq.bik(rnd)
    return Org(
        name=f"{kind} «{rnd.choice(NAMES)}-{rnd.randint(10, 99)}»",
        inn=inn,
        kpp=rq.kpp(inn, rnd),
        ogrn=rq.ogrn13(rnd, rnd.randint(2012, 2021)),
        bik=b,
        account=rq.account(b, rnd),
        head=rnd.choice(HEADS),
        address=f"г. Синтетск, ул. Тестовая, д. {rnd.randint(1, 120)}",
    )


def weekday(d: dt.date) -> dt.date:
    while d.weekday() >= 5:
        d -= dt.timedelta(days=1)
    return d


PROJECTS = [
    "Модернизация линии механической обработки",
    "Цифровая платформа контроля качества",
    "Производство композитных панелей",
    "Энергоэффективная сушильная камера",
]
DEFECTS = ["share", "mismatch", "expired", "inn"]
BANKS = ["АО «Синтетбанк»", "ПАО «Банк Тестовый»", "АО «Северный кредит»"]


def build(seed: int, today: dt.date, defect: str | None = None) -> list[Doc]:
    """Пакет программы «Инновационный проект» (кейс 3): 6 обязательных видов + согласие, плюс сертификат,
    документ на недвижимость и декларация ГИМС — все 9 видов, чтобы проверить классификацию целиком.

    defect: share — собственных средств меньше 50 %; mismatch — сумма в заявлении ≠ обоснованию;
    expired — сертификат просрочен; inn — ИНН в гарантийном письме другой.
    """
    rnd = random.Random(seed)
    app, guarantor = org(rnd, "ООО"), org(rnd, "АО")
    fio = app.head
    project = rnd.choice(PROJECTS)
    items = []
    for name, unit in rnd.sample(GOODS, 3):
        qty = rnd.randint(1, 3) if unit == "шт" else 1
        price = Decimal(rnd.randint(500_000, 30_000_000)) / 100
        items.append((name, unit, qty, price, (price * qty).quantize(CENT)))
    total = sum((i[4] for i in items), Decimal("0"))
    share = Decimal("0.42") if defect == "share" else Decimal(rnd.randint(50, 70)) / 100
    own = (total * share).quantize(CENT, ROUND_HALF_UP)
    requested = total - own
    app_requested = requested + Decimal("10000.00") if defect == "mismatch" else requested
    pct = (own * 100 / total).quantize(Decimal("1"), ROUND_HALF_UP)
    d_app = weekday(today - dt.timedelta(days=3))
    d_bank = weekday(today - dt.timedelta(days=6))
    valid_cert = today - dt.timedelta(days=10) if defect == "expired" else today + dt.timedelta(days=400)
    guarantor_inn = rq.inn10(rnd) if defect == "inn" else app.inn
    balance = (own * Decimal("1.3")).quantize(CENT)
    cadastral = (
        f"{rnd.randint(10, 89)}:{rnd.randint(10, 60):02d}:{rnd.randint(100000, 9999999):07d}:{rnd.randint(1, 999)}"
    )
    rows = [["№", "Статья расходов", "Ед.", "Кол-во", "Цена, руб.", "Сумма, руб."]] + [
        [str(i + 1), n, u, str(q), money(p), money(x)] for i, (n, u, q, p, x) in enumerate(items)
    ]
    passport = f"{rnd.randint(10, 99)} {rnd.randint(10, 99)} {rnd.randint(100000, 999999)}"
    return [
        Doc(
            "APPLICATION",
            "ЗАЯВКА на участие в отборе на предоставление субсидии",
            [
                "В Агентство инноваций (программа «Инновационный проект»)",
                f"Заявитель: {app.name}",
                f"ИНН {app.inn}   ОГРН {app.ogrn}",
                f"Адрес: {app.address}",
                f"Наименование проекта: {project}",
                f"Общий бюджет проекта: {money(total)} руб.",
                f"Софинансирование за счёт собственных средств: {money(own)} руб. ({pct} %)",
                f"Запрашиваемая сумма субсидии: {money(app_requested)} руб. ({rubles(app_requested)})",
                "Прошу предоставить субсидию на реализацию проекта.",
                f"Дата: {d_app:%d.%m.%Y}",
                f"Генеральный директор __________ {fio}",
            ],
            {
                "inn": app.inn,
                "ogrn": app.ogrn,
                "requestedAmount": str(app_requested),
                "totalCost": str(total),
                "ownFunds": str(own),
                "signerName": fio,
            },
        ),
        Doc(
            "PD_CONSENT",
            "СОГЛАСИЕ на обработку персональных данных",
            [
                f"Я, {fio}, паспорт {passport},",
                f"зарегистрированный(ая) по адресу: {app.address},",
                "в соответствии со ст. 9 Федерального закона от 27.07.2006 № 152-ФЗ «О персональных данных»",
                "даю согласие Агентству инноваций на обработку моих персональных данных",
                "в целях рассмотрения заявки на предоставление субсидии.",
                "Согласие действует до его отзыва в письменной форме.",
                f"Дата: {d_app:%d.%m.%Y}          Подпись __________ {fio}",
            ],
            {"fullName": fio},
        ),
        Doc(
            "PRESENTATION",
            f"ПРЕЗЕНТАЦИЯ ПРОЕКТА «{project}»",
            [
                f"Команда: {app.name}",
                "Проблема: ручные операции и брак до 7 % на участке обработки.",
                "Решение: внедрение оборудования с ЧПУ и цифрового контроля параметров.",
                "Рынок: предприятия машиностроения региона, 120+ потенциальных заказчиков.",
                f"Бюджет: {money(total)} руб., из них собственные средства {pct} %.",
                "Эффект: рост выработки на 35 %, снижение брака до 1,5 %, 14 новых рабочих мест.",
                "Сроки: 12 месяцев с даты получения субсидии.",
            ],
            {"projectName": project},
        ),
        Doc(
            "COST_JUSTIFICATION",
            "ОБОСНОВАНИЕ РАЗМЕРА ЗАТРАТ на реализацию проекта",
            [f"Заявитель: {app.name}, ИНН {app.inn}", f"Проект: {project}"],
            {"inn": app.inn, "totalCost": str(total), "ownFunds": str(own), "requestedAmount": str(requested)},
            rows,
            [
                f"Итого {money(total)}",
                f"Собственные средства: {money(own)} руб.",
                f"Средства субсидии: {money(requested)} руб.",
            ],
        ),
        Doc(
            "BANK_STATEMENT",
            "СПРАВКА о наличии расчётного счёта и остатке денежных средств",
            [
                f"{rnd.choice(BANKS)}, БИК {app.bik}",
                f"Настоящим подтверждаем, что {app.name} (ИНН {app.inn})",
                f"имеет расчётный счёт № {app.account}.",
                f"Остаток денежных средств на {d_bank:%d.%m.%Y}: {money(balance)} руб.",
                "Картотека неоплаченных документов к счёту отсутствует.",
                f"Дата выдачи: {d_bank:%d.%m.%Y}",
                "Начальник операционного отдела __________ Петрова А. В.",
            ],
            {"inn": app.inn, "account": app.account, "bik": app.bik, "balance": str(balance)},
        ),
        Doc(
            "GUARANTEE_LETTER",
            "ГАРАНТИЙНОЕ ПИСЬМО",
            [
                f"{guarantor.name} (ИНН {guarantor_inn if defect == 'inn' else guarantor.inn})",
                f"гарантирует обеспечение софинансирования проекта «{project}»",
                f"заявителя {app.name}, ИНН {guarantor_inn},",
                f"в размере {money(own)} руб. ({rubles(own)}).",
                f"Дата: {d_app:%d.%m.%Y}",
                f"Генеральный директор __________ {guarantor.head}",
            ],
            {"inn": guarantor_inn, "amount": str(own)},
        ),
        Doc(
            "CERTIFICATE",
            f"СЕРТИФИКАТ СООТВЕТСТВИЯ № РОСС RU.{rnd.randint(1000, 9999)}.{rnd.randint(10000, 99999)}",
            [
                "Орган по сертификации: ООО «Синтет-Стандарт»",
                f"Изготовитель: {app.name}, ИНН {app.inn}",
                "Продукция: панели композитные, серийный выпуск",
                "Соответствует требованиям ТР ТС 010/2011",
                f"Дата выдачи: {valid_cert - dt.timedelta(days=1095):%d.%m.%Y}",
                f"Срок действия по {valid_cert:%d.%m.%Y} включительно",
            ],
            {"inn": app.inn, "validUntil": valid_cert.isoformat()},
        ),
        Doc(
            "PROPERTY_DOC",
            "ВЫПИСКА из Единого государственного реестра недвижимости",
            [
                "об основных характеристиках и зарегистрированных правах на объект недвижимости",
                f"Кадастровый номер: {cadastral}",
                f"Адрес: {app.address}",
                f"Площадь: {rnd.randint(300, 4000)} кв. м",
                "Назначение: нежилое, производственное",
                f"Правообладатель: {app.name}, ИНН {app.inn}",
                f"Дата выписки: {d_bank:%d.%m.%Y}",
            ],
            {"cadastralNumber": cadastral},
        ),
        Doc(
            "GIMS_DECLARATION",
            "ДЕКЛАРАЦИЯ о безопасности пляжа (ГИМС МЧС России)",
            [
                f"№ {rnd.randint(10, 99)}-{rnd.randint(100, 999)}/ГИМС",
                f"Эксплуатирующая организация: {app.name}, ИНН {app.inn}",
                "Пляж: г. Синтетск, набережная Тестового озера, участок 3",
                "Освидетельствован государственным инспектором по маломерным судам.",
                f"Действительна до {today + dt.timedelta(days=200):%d.%m.%Y}",
            ],
            {"inn": app.inn, "validUntil": (today + dt.timedelta(days=200)).isoformat()},
        ),
    ]


def draw(c: Canvas, f: str, doc: Doc) -> None:
    w, h = A4
    y = h - 60
    c.setFont(f, 13)
    c.drawString(50, y, doc.title)
    y -= 30
    c.setFont(f, 10)
    for line in doc.lines:
        c.drawString(50, y, line)
        y -= 17
    if doc.table:
        xs = [50, 75, 300, 345, 395, 470, w - 40]
        y -= 6
        for row in doc.table:
            c.line(xs[0], y + 13, xs[-1], y + 13)
            for i, cell in enumerate(row):
                c.drawString(xs[i] + 3, y, cell[:44])
            y -= 18
        c.line(xs[0], y + 13, xs[-1], y + 13)
        top = y + 13 + 18 * len(doc.table)
        for x in xs:
            c.line(x, top, x, y + 13)
        y -= 10
    for line in doc.after:
        c.drawString(50, y, line)
        y -= 17
    c.showPage()


def write(out: Path, seed: int, today: dt.date, merged: bool, defect: str | None) -> dict[str, Any]:
    out.mkdir(parents=True, exist_ok=True)
    f = font()
    docs = build(seed, today, defect)
    rnd = random.Random(seed + 1)
    groups: list[list[Doc]] = [[d] for d in docs]
    if merged:  # заявление + согласие + презентация в одном PDF (сегментация по содержимому)
        groups = [docs[:3]] + [[d] for d in docs[3:]]
    rnd.shuffle(groups)
    labels: dict[str, Any] = {"seed": seed, "date": today.isoformat(), "defect": defect, "files": {}}
    for g in groups:
        name = f"doc-{rnd.randint(1000, 9999)}.pdf"
        c = Canvas(str(out / name), pagesize=A4)
        c.setTitle("document")
        for d in g:
            draw(c, f, d)
        c.save()
        labels["files"][name] = [
            {"fromPage": i + 1, "toPage": i + 1, "type": d.type, "fields": d.fields} for i, d in enumerate(g)
        ]
    labels["expectedRules"] = {
        "OWN_FUNDS_SHARE": "FAILED" if defect == "share" else "PASSED",
        "REQUEST_MATCH": "FAILED" if defect == "mismatch" else "PASSED",
        "NOT_EXPIRED": "FAILED" if defect == "expired" else "PASSED",
        "INN_SAME": "FAILED" if defect == "inn" else "PASSED",
    }
    (out / "labels.json").write_text(json.dumps(labels, ensure_ascii=False, indent=1), encoding="utf-8")
    return labels


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out", required=True, type=Path)
    p.add_argument("--seed", type=int, default=1)
    p.add_argument("--date", type=dt.date.fromisoformat, default=dt.date.today())
    p.add_argument("--merged", action="store_true")
    p.add_argument("--defect", choices=DEFECTS)
    a = p.parse_args()
    labels = write(a.out, a.seed, a.date, a.merged, a.defect)
    print(json.dumps({k: [s["type"] for s in v] for k, v in labels["files"].items()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
