"""Transactional mail for Digital Hustle; no credentials or codes in logs."""
from html import escape

SERVICES = {
    "proxodnaya": ("Проходная", "https://proxodnaya.digital-hustle.ru/login"),
    "documents": ("Документы", "https://documents.digital-hustle.ru/operator"),
    "subsidy": ("Субсидии", "https://subsidy.digital-hustle.ru/specialist"),
}


def render(service: str, kind: str, code: str = "", minutes: int = 10,
           name: str = "", role: str = "", invited_by: str = "") -> tuple[str, str, str]:
    title, url = SERVICES[service]
    if kind == "login":
        subject = f"{title} — код входа в Digital Hustle"
        heading = "Ваш код для входа"
        body = f"Вы запросили вход в сервис «{title}». Код действует {minutes} мин. и используется один раз."
        detail = f'<p style="font-size:36px;letter-spacing:10px;font-weight:700;margin:24px 0">{escape(code)}</p>'
        plain = f"{body}\n\nКод входа: {code}"
    else:
        subject = f"{title} — приглашение в Digital Hustle"
        heading = f"Здравствуйте, {name}!"
        body = f"{invited_by} приглашает вас в сервис «{title}» с ролью «{role}». Войдите по почте — пароль не нужен."
        detail = f'<p><a href="{url}" style="color:#148F2B;font-weight:600">Открыть кабинет</a></p>'
        plain = body
    warning = "Никому не сообщайте код: сотрудники Digital Hustle его не запрашивают. Если вы не запрашивали вход, проигнорируйте письмо."
    plain = f"Digital Hustle · {title}\n\n{plain}\n\n{url}\n\n{warning}\n\nЭто автоматическое письмо, отвечать на него не нужно."
    html = f'''<!doctype html><html lang="ru"><body style="margin:0;background:#F5FBF7;font-family:Arial,sans-serif;color:#172419">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 16px">
<table role="presentation" width="100%" style="max-width:560px;background:#FFFFFF;border-radius:20px" cellpadding="0" cellspacing="0">
<tr><td style="padding:32px"><p style="color:#148F2B;font-size:14px;font-weight:700">DIGITAL HUSTLE · {escape(title)}</p>
<h1 style="font-size:26px;margin:24px 0 16px">{escape(heading)}</h1>
<p style="line-height:1.6">{escape(body)}</p>{detail}
<p style="font-size:14px;line-height:1.6;color:#52605B">{escape(warning)}</p>
<hr style="border:0;border-top:1px solid #D6F2E1;margin:24px 0">
<p style="font-size:12px;color:#52605B">Автоматическое письмо от no-reply@digital-hustle.ru. Отвечать на него не нужно.</p>
</td></tr></table></td></tr></table></body></html>'''
    return subject, plain, html
