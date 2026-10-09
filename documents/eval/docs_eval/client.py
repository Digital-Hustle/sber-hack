"""Клиент публичного API: грузим файл как соискатель, ждём итоговую стадию, читаем результат как оператор.

Только стандартная библиотека. Оператору нужен access-токен Keycloak (realm documents): прямой выдачи по паролю
у клиента documents-operator нет, поэтому токен передаётся снаружи (EVAL_OPERATOR_TOKEN).
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass

from .metrics import DONE_STAGES


class ApiError(RuntimeError):
    pass


@dataclass
class DocResult:
    stage: str | None
    type: str | None
    fields: dict[str, tuple[str | None, float | None]]
    seconds: float


class Api:
    def __init__(self, base: str, operator_token: str | None, timeout: float = 30.0) -> None:
        self.base = base.rstrip("/")
        self.operator_token = operator_token
        self.timeout = timeout

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
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as rs:
                raw = rs.read()
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:300]
            raise ApiError(f"{method} {path} → {e.code}: {detail}") from e
        return json.loads(raw) if raw else {}

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

    def wait_stage(self, app_id: str, key: str, doc_id: str, deadline_s: float, poll_s: float = 2.0) -> str | None:
        """Ждём итоговую стадию документа (PARSED, NEEDS_REVIEW, UNREADABLE, FAILED…) через кабинет соискателя."""
        stop = time.monotonic() + deadline_s
        stage = None
        while time.monotonic() < stop:
            app = self._call("GET", f"/intake/api/v1/applicant/applications/{app_id}", auth=f"Applicant {key}")
            doc = next((d for d in app.get("documents", []) if d.get("id") == doc_id), None)
            stage = doc.get("stage") if doc else None
            if stage in DONE_STAGES:
                return stage
            time.sleep(poll_s)
        return stage

    def result(self, doc_id: str) -> dict:
        if not self.operator_token:
            raise ApiError("нет EVAL_OPERATOR_TOKEN — поля прочитать нельзя")
        return self._call("GET", f"/docproc/api/v1/documents/{doc_id}/result", auth=f"Bearer {self.operator_token}")

    def process(self, name: str, data: bytes, mime: str, deadline_s: float) -> DocResult:
        """Один документ — одна заявка (документы не влияют друг на друга)."""
        started = time.monotonic()
        app_id, key = self.create_application()
        doc_id = self.upload(app_id, key, name, data, mime)
        stage = self.wait_stage(app_id, key, doc_id, deadline_s)
        seconds = time.monotonic() - started
        if stage not in DONE_STAGES:
            raise ApiError(f"не дождались итоговой стадии за {deadline_s:.0f} с (последняя: {stage})")
        rs = self.result(doc_id)
        fields = {f["name"]: (f.get("value"), f.get("confidence")) for f in rs.get("fields") or [] if f.get("name")}
        return DocResult(stage=rs.get("stage") or stage, type=rs.get("type"), fields=fields, seconds=seconds)
