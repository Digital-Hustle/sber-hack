"""One SMTP client for all three cases; internal API only."""
import hmac
import os
import smtplib
import ssl
from email.message import EmailMessage
from typing import Annotated, Literal

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, model_validator

from templates import render

app = FastAPI(title="Digital Hustle Mail", docs_url=None, redoc_url=None, openapi_url=None)


class MailRequest(BaseModel):
    service: Literal["proxodnaya", "documents", "subsidy"]
    kind: Literal["login", "invitation"]
    email: str = Field(pattern=r"^[^\s@<>\r\n]+@[^\s@<>\r\n]+\.[^\s@<>\r\n]+$", max_length=254)
    code: str = Field(default="", pattern=r"^([0-9]{6})?$", max_length=6)
    minutes: int = Field(default=10, ge=1, le=60)
    name: str = Field(default="", max_length=200)
    role: str = Field(default="", max_length=100)
    invited_by: str = Field(default="", max_length=200)

    @model_validator(mode="after")
    def login_needs_code(self) -> "MailRequest":
        if self.kind == "login" and not self.code:
            raise ValueError("Login mail requires a code")
        return self


def authorize(authorization: Annotated[str | None, Header()] = None) -> None:
    expected = os.environ.get("SHARED_MAIL_TOKEN", "")
    if len(expected) < 32 or not hmac.compare_digest(authorization or "", "Bearer " + expected):
        raise HTTPException(status_code=401, detail="Unauthorized")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "smtp": "configured" if os.environ.get("SMTP_HOST") else "not_configured"}


@app.post("/internal/mail", status_code=204, dependencies=[Depends(authorize)])
def send_mail(request: MailRequest) -> None:
    host = os.environ.get("SMTP_HOST", "")
    if not host:
        raise HTTPException(status_code=503, detail="Почта ещё не подключена. Настройте SMTP для Digital Hustle.")
    message = EmailMessage()
    subject, plain, html = render(request.service, request.kind, request.code, request.minutes,
                                  request.name, request.role, request.invited_by)
    message["Subject"] = subject
    message["From"] = "Digital Hustle <no-reply@digital-hustle.ru>"
    message["To"] = request.email
    message.set_content(plain)
    message.add_alternative(html, subtype="html")
    mode = os.environ.get("SMTP_TLS", "starttls")
    if mode not in ("starttls", "ssl"):
        raise HTTPException(status_code=503, detail="SMTP требует TLS")
    context = ssl.create_default_context()
    client_type = smtplib.SMTP_SSL if mode == "ssl" else smtplib.SMTP
    kwargs = {"context": context} if mode == "ssl" else {}
    try:
        with client_type(host, int(os.environ.get("SMTP_PORT", "587")), timeout=5, **kwargs) as smtp:
            if mode == "starttls":
                smtp.starttls(context=context)
            user = os.environ.get("SMTP_USER", "no-reply@digital-hustle.ru")
            smtp.login(user, os.environ.get("SMTP_PASSWORD", ""))
            smtp.send_message(message)
    except (OSError, smtplib.SMTPException, ValueError):
        # Never expose provider responses: they may contain addresses or credentials.
        raise HTTPException(status_code=503, detail="Не удалось отправить письмо. Попробуйте позже.") from None
