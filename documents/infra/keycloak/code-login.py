#!/usr/bin/env python3
"""FE-UI-03: вход только «логин + код» на УЖЕ развёрнутом стенде (новый получает то же из realm-documents.json).

Повторный запуск безопасен. Запуск из infra/:  python3 keycloak/code-login.py
Делает: потоки documents-code (браузер: логин → код, без кода — отказ) и documents-code-direct (форма фронта),
снимает пароли, выдаёт демо-учёткам коды из .env (KC_DEMO_*_TOTP, нет — генерирует и дописывает в .env),
даёт svc-review права manage-users/view-users/query-users (раздел «Сотрудники»).
"""
import json
import secrets
import string
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ENV = Path(".env")
REALM = "/admin/realms/documents"
DEMO = {"operator": "KC_DEMO_OPERATOR_TOTP", "senior": "KC_DEMO_SENIOR_TOTP", "auditor": "KC_DEMO_AUDITOR_TOTP"}
CD = json.dumps({"subType": "totp", "digits": 6, "period": 30, "algorithm": "HmacSHA1", "counter": 0})


def env() -> dict:
    return dict(l.split("=", 1) for l in ENV.read_text().splitlines() if "=" in l and not l.startswith("#"))


def ensure_secrets() -> dict:
    e = env()
    add = [k for k in DEMO.values() if not e.get(k)]
    if add:
        alphabet = string.ascii_letters + string.digits
        with ENV.open("a") as f:
            for k in add:
                v = "".join(secrets.choice(alphabet) for _ in range(32))
                f.write(f"{k}={v}\n")
                e[k] = v
    return e


def base_url() -> str:
    cid = subprocess.check_output(["docker", "compose", "ps", "-q", "keycloak"], text=True).strip()
    ip = subprocess.check_output(["docker", "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}",
                                  cid], text=True).split()[0]
    return f"http://{ip}:8080/auth"


class Kc:
    def __init__(self, url: str, user: str, password: str):
        self.url = url
        t = self.call("POST", "/realms/master/protocol/openid-connect/token",
                      {"grant_type": "password", "client_id": "admin-cli", "username": user, "password": password},
                      form=True)
        self.token = t["access_token"]

    def call(self, method, path, body=None, form=False, ok=(200, 201, 204)):
        headers = {}
        data = None
        if getattr(self, "token", None):
            headers["Authorization"] = "Bearer " + self.token
        if body is not None:
            data = urllib.parse.urlencode(body).encode() if form else json.dumps(body).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
        req = urllib.request.Request(self.url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as r:
                raw = r.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as err:
            if err.code in ok:
                return None
            raise SystemExit(f"{method} {path}: {err.code} {err.read().decode()[:300]}")


def flow(kc: Kc, parent: str | None, alias: str, steps: list) -> None:
    """Создать поток (или подпоток в parent) и его шаги: ("auth", provider, req, config?) / ("flow", alias, req, steps)."""
    if parent is None:
        kc.call("POST", f"{REALM}/authentication/flows",
                {"alias": alias, "providerId": "basic-flow", "topLevel": True, "builtIn": False, "description": "FE-UI-03"})
    else:
        kc.call("POST", f"{REALM}/authentication/flows/{urllib.parse.quote(parent)}/executions/flow",
                {"alias": alias, "type": "basic-flow", "provider": "registration-page-form", "description": "FE-UI-03"})
    for kind, name, req, extra in steps:
        if kind == "auth":
            kc.call("POST", f"{REALM}/authentication/flows/{alias}/executions/execution", {"provider": name})
        else:
            flow(kc, alias, name, extra)
        ex = [x for x in kc.call("GET", f"{REALM}/authentication/flows/{alias}/executions") if x["level"] == 0][-1]
        ex["requirement"] = req
        kc.call("PUT", f"{REALM}/authentication/flows/{alias}/executions", ex)
        if kind == "auth" and extra:
            kc.call("POST", f"{REALM}/authentication/executions/{ex['id']}/config", extra)


def main() -> None:
    e = ensure_secrets()
    kc = Kc(base_url(), e["KC_BOOTSTRAP_ADMIN_USERNAME"], e["KC_BOOTSTRAP_ADMIN_PASSWORD"])
    aliases = {f["alias"] for f in kc.call("GET", f"{REALM}/authentication/flows")}
    if "documents-code-deny" not in aliases:
        kc.call("PUT", REALM, {"browserFlow": "browser", "directGrantFlow": "direct grant"})
        for a in ("documents-code", "documents-code-direct"):
            fl = [f for f in kc.call("GET", f"{REALM}/authentication/flows") if f["alias"] == a]
            if fl:
                kc.call("DELETE", f"{REALM}/authentication/flows/{fl[0]['id']}")
        for a in ("documents-code-forms", "documents-code-otp", "documents-code-password"):  # осиротевшие подпотоки
            fl = [f for f in kc.call("GET", f"{REALM}/authentication/flows") if f["alias"] == a]
            if fl:
                kc.call("DELETE", f"{REALM}/authentication/flows/{fl[0]['id']}", ok=(204, 400, 404))
        flow(kc, None, "documents-code", [
            ("auth", "auth-cookie", "ALTERNATIVE", None),
            ("flow", "documents-code-forms", "ALTERNATIVE", [
                ("auth", "auth-username-form", "REQUIRED", None),
                ("flow", "documents-code-otp", "CONDITIONAL", [
                    ("auth", "conditional-user-configured", "REQUIRED", None),
                    ("auth", "auth-otp-form", "REQUIRED", None)]),
                ("flow", "documents-code-deny", "CONDITIONAL", [
                    ("auth", "conditional-sub-flow-executed", "REQUIRED",
                     {"alias": "documents-code-otp-not-executed",
                      "config": {"flow_to_check": "documents-code-otp", "check_result": "not-executed"}}),
                    ("auth", "deny-access-authenticator", "REQUIRED",
                     {"alias": "documents-code-no-code",
                      "config": {"denyErrorMessage": "Вход не настроен: попросите старшего оператора выдать код"}})])])])
        flow(kc, None, "documents-code-direct", [
            ("auth", "direct-grant-validate-username", "REQUIRED", None),
            ("auth", "direct-grant-validate-otp", "REQUIRED", None)])
    kc.call("PUT", REALM, {"browserFlow": "documents-code", "directGrantFlow": "documents-code-direct"})

    op = kc.call("GET", f"{REALM}/clients?clientId=documents-operator")[0]
    if not op.get("directAccessGrantsEnabled"):
        op["directAccessGrantsEnabled"] = True
        kc.call("PUT", f"{REALM}/clients/{op['id']}", op)

    svc = kc.call("GET", f"{REALM}/clients?clientId=svc-review")[0]
    sa = kc.call("GET", f"{REALM}/clients/{svc['id']}/service-account-user")
    rm = kc.call("GET", f"{REALM}/clients?clientId=realm-management")[0]
    want = [r for r in kc.call("GET", f"{REALM}/clients/{rm['id']}/roles")
            if r["name"] in ("manage-users", "view-users", "query-users")]
    kc.call("POST", f"{REALM}/users/{sa['id']}/role-mappings/clients/{rm['id']}", want)

    for login, key in DEMO.items():
        found = kc.call("GET", f"{REALM}/users?exact=true&username={login}")
        if not found:
            continue
        uid = found[0]["id"]
        for c in kc.call("GET", f"{REALM}/users/{uid}/credentials"):
            if c["type"] in ("password", "otp"):
                kc.call("DELETE", f"{REALM}/users/{uid}/credentials/{c['id']}")
        kc.call("PUT", f"{REALM}/users/{uid}", {"requiredActions": [], "credentials": [
            {"type": "otp", "userLabel": "Приложение-аутентификатор",
             "secretData": json.dumps({"value": e[key]}), "credentialData": CD}]})
        kc.call("POST", f"{REALM}/users/{uid}/logout")
    print("вход только по коду включён; QR демо-учёток: python3 keycloak/demo-qr.py <логин>")


if __name__ == "__main__":
    main()
