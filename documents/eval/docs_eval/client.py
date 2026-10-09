"""Клиент публичного API: грузим файл как соискатель, ждём итоговую стадию, читаем результат как оператор.

Только стандартная библиотека. Оператору нужен access-токен Keycloak (realm documents): прямой выдачи по паролю
у клиента documents-operator нет, поэтому токен передаётся снаружи (EVAL_OPERATOR_TOKEN) или берётся по паролю
у отдельного клиента стенда замера (`scripts/eval-client.sh`, EVAL_TOKEN_URL) и обновляется перед каждым чтением.
"""

from __future__ import annotations

import json
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from collections.abc import Callable
from dataclasses import dataclass

from .metrics import DONE_STAGES


class ApiError(RuntimeError):
    pass


@dataclass
class DocResult:
    stage: str | None
    type: str | None
    fields: dict[str, tuple[str | None, float | None]] | None
    seconds: float


class PasswordToken:
    """Токен оператора по паролю (grant_type=password) с обновлением до истечения — для долгих прогонов на CPU."""

    def __init__(self, url: str, client_id: str, username: str, password: str, insecure: bool = False) -> None:
        self.url, self.client_id, self.username, self.password = url, client_id, username, password
        self.ssl = ssl._create_unverified_context() if insecure else None
        self.token: str | None = None
        self.until = 0.0

    def __call__(self) -> str:
        if self.token and time.monotonic() < self.until:
            return self.token
        body = urllib.parse.urlencode({"grant_type": "password", "client_id": self.client_id,
                                       "username": self.username, "password": self.password}).encode()
        req = urllib.request.Request(self.url, data=body, method="POST")
        req.add_header("Content-Type", "application/x-www-form-urlencoded")
        try:
            with urllib.request.urlopen(req, timeout=30, context=self.ssl) as rs:
                data = json.loads(rs.read())
        except urllib.error.HTTPError as e:
            raise ApiError(f"токен оператора: {e.code} {e.read().decode('utf-8', 'replace')[:200]}") from e
        self.token = data["access_token"]
        self.until = time.monotonic() + max(int(data.get("expires_in", 60)) - 30, 10)
        return self.token


class Api:
    def __init__(self, base: str, operator_token: str | Callable[[], str] | None, timeout: float = 30.0,
                 insecure: bool = False) -> None:
        self.base = base.rstrip("/")
        self.operator_token = operator_token
        self.timeout = timeout
        self.retries_429 = 10
        # Локальный стенд за Caddy с внутренним CA (https://localhost) — только по явному флагу.
        self.ssl = ssl._create_unverified_context() if insecure else None

    def _call(self, method: str, path: str, *, auth: str | None, body: bytes | None = None,
              ctype: str | None = None, headers: dict[str, str] | None = None) -> dict:
        req = urllib.request.Request(self.base + path, data=body, method=method)
        req.add_header("Accept", "application/json")
        if auth:
            req.add_header("Authorization", auth)
        if ctype:
            req.add_header("Content-Type", ctype)
        for k, v in (headers or {}).items():
            req.add_header(k, v)
        for attempt in range(self.retries_429 + 1):
            try:
                with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl) as rs:
                    raw = rs.read()
                return json.loads(raw) if raw else {}
            except urllib.error.HTTPError as e:
                if e.code == 429 and attempt < self.retries_429:
                    # Лимит частоты gateway (FR-06): ждём, сколько просит Retry-After, и повторяем.
                    time.sleep(min(float(e.headers.get("Retry-After") or 5), 120.0))
                    continue
                detail = e.read().decode("utf-8", "replace")[:300]
                raise ApiError(f"{method} {path} → {e.code}: {detail}") from e
        raise ApiError(f"{method} {path} → 429")

    def create_application(self) -> tuple[str, str]:
        rs = self._call("POST", "/intake/api/v1/applicant/applications", auth=None,
                        body=json.dumps({"consentPd": True}).encode(), ctype="application/json")
        return rs["applicationId"], rs["accessKey"]

    def upload(self, app_id: str, key: str, name: str, data: bytes, mime: str) -> str:
        boundary = uuid.uuid4().hex
        body = (
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
            f"Content-Type: {mime}\r\n\r\n"
        ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
        rs = self._call("POST", f"/intake/api/v1/applicant/applications/{app_id}/documents", auth=f"Applicant {key}",
                        body=body, ctype=f"multipart/form-data; boundary={boundary}",
                        headers={"Idempotency-Key": uuid.uuid4().hex})
        return rs["documentId"]

    def wait_stage(
        self, app_id: str, key: str, doc_id: str, deadline_s: float, poll_s: float = 2.0
    ) -> tuple[str | None, str | None]:
        """Ждём итоговую стадию документа через кабинет соискателя. Возвращает (стадия, определённый тип)."""
        stop = time.monotonic() + deadline_s
        stage = detected = None
        while time.monotonic() < stop:
            app = self._call("GET", f"/intake/api/v1/applicant/applications/{app_id}", auth=f"Applicant {key}")
            doc = next((d for d in app.get("documents", []) if d.get("id") == doc_id), None)
            stage = doc.get("stage") if doc else None
            detected = doc.get("detectedType") if doc else None
            if stage in DONE_STAGES:
                return stage, detected
            time.sleep(poll_s)
        return stage, detected

    def result(self, doc_id: str) -> dict:
        if not self.operator_token:
            raise ApiError("нет EVAL_OPERATOR_TOKEN — поля прочитать нельзя")
        token = self.operator_token() if callable(self.operator_token) else self.operator_token
        return self._call("GET", f"/docproc/api/v1/documents/{doc_id}/result", auth=f"Bearer {token}")

    def process(self, name: str, data: bytes, mime: str, deadline_s: float) -> DocResult:
        """Один документ — одна заявка (документы не влияют друг на друга)."""
        started = time.monotonic()
        app_id, key = self.create_application()
        doc_id = self.upload(app_id, key, name, data, mime)
        stage, detected = self.wait_stage(app_id, key, doc_id, deadline_s)
        seconds = time.monotonic() - started
        if stage not in DONE_STAGES:
            raise ApiError(f"не дождались итоговой стадии за {deadline_s:.0f} с (последняя: {stage})")
        if not self.operator_token:
            # Без токена оператора меряем только то, что видит соискатель: стадию и тип; поля — не меряются.
            return DocResult(stage=stage, type=detected, fields=None, seconds=seconds)
        rs = self.result(doc_id)
        fields = {f["name"]: (f.get("value"), f.get("confidence")) for f in rs.get("fields") or [] if f.get("name")}
        return DocResult(stage=rs.get("stage") or stage, type=rs.get("type"), fields=fields, seconds=seconds)
