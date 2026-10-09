#!/usr/bin/env python3
"""FE-UI-04: вход по коду из письма на УЖЕ развёрнутом стенде (новый получает то же из realm-documents.json).

Повторный запуск безопасен. Запуск из infra/:  python3 keycloak/email-login.py
Делает: вход по почте (loginWithEmailAllowed) и запрет дублей почты, проставляет демо-учёткам
почту из .env (KC_DEMO_*_EMAIL, по умолчанию <логин>@documents.local), закрывает старые сессии realm.
"""
import importlib.util
from pathlib import Path

_spec = importlib.util.spec_from_file_location("code_login", Path(__file__).with_name("code-login.py"))
cl = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cl)

REALM = cl.REALM
DEMO = {"operator": "KC_DEMO_OPERATOR_EMAIL", "senior": "KC_DEMO_SENIOR_EMAIL", "auditor": "KC_DEMO_AUDITOR_EMAIL"}


def main() -> None:
    e = cl.env()
    kc = cl.Kc(cl.base_url(), e["KC_BOOTSTRAP_ADMIN_USERNAME"], e["KC_BOOTSTRAP_ADMIN_PASSWORD"])
    kc.call("PUT", REALM, {"loginWithEmailAllowed": True, "duplicateEmails": False})
    for login, key in DEMO.items():
        email = (e.get(key) or f"{login}@documents.local").strip().lower()
        users = kc.call("GET", f"{REALM}/users?username={login}&exact=true") or []
        if not users:
            print(f"— {login}: нет учётки, пропускаю")
            continue
        u = users[0]
        if (u.get("email") or "").lower() != email or not u.get("emailVerified"):
            kc.call("PUT", f"{REALM}/users/{u['id']}", {"email": email, "emailVerified": True})
        print(f"✔ {login}: {email}")
    kc.call("POST", f"{REALM}/logout-all", ok=(200, 204))
    print("✔ вход по почте включён, сессии закрыты; далее: docker compose restart gateway")


if __name__ == "__main__":
    main()
