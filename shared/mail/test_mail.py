import os
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from app import app
from templates import render


class MailTests(unittest.TestCase):
    def test_every_case_has_subject_plain_and_html(self):
        for service in ("proxodnaya", "documents", "subsidy"):
            subject, plain, html = render(service, "login", "123456")
            self.assertNotIn("123456", subject)
            self.assertIn("123456", plain)
            self.assertIn("123456", html)
            self.assertIn("10 мин.", html)
            self.assertIn("no-reply@digital-hustle.ru", html)

    def test_invitation_escapes_user_text(self):
        _, _, html = render("documents", "invitation", name='<script>alert(1)</script>')
        self.assertNotIn("<script>", html)
        self.assertIn("&lt;script&gt;", html)

    def test_api_auth_missing_smtp_and_validation(self):
        with patch.dict(os.environ, {"SHARED_MAIL_TOKEN": "x" * 32, "SMTP_HOST": ""}):
            client = TestClient(app)
            payload = {"service": "proxodnaya", "kind": "login", "email": "user@example.com", "code": "123456"}
            self.assertEqual(client.post("/internal/mail", json=payload).status_code, 401)
            headers = {"Authorization": "Bearer " + "x" * 32}
            response = client.post("/internal/mail", json=payload, headers=headers)
            self.assertEqual(response.status_code, 503)
            self.assertIn("Почта ещё не подключена", response.json()["detail"])
            payload["email"] = "user@example.com\r\nBcc: other@example.com"
            self.assertEqual(client.post("/internal/mail", json=payload, headers=headers).status_code, 422)

    def test_tls_send_and_no_code_in_subject(self):
        with patch.dict(os.environ, {"SHARED_MAIL_TOKEN": "x" * 32, "SMTP_HOST": "smtp.example.com", "SMTP_TLS": "starttls"}), \
                patch("app.smtplib.SMTP") as smtp:
            response = TestClient(app).post("/internal/mail", headers={"Authorization": "Bearer " + "x" * 32},
                json={"service": "subsidy", "kind": "login", "email": "user@example.com", "code": "123456"})
            self.assertEqual(response.status_code, 204)
            client = smtp.return_value.__enter__.return_value
            client.starttls.assert_called_once()
            message = client.send_message.call_args.args[0]
            self.assertNotIn("123456", message["Subject"])
            self.assertEqual(message["From"], "Digital Hustle <no-reply@digital-hustle.ru>")


if __name__ == "__main__":
    unittest.main()
