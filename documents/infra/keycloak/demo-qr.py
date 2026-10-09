#!/usr/bin/env python3
"""Ссылка otpauth:// для демо-учётки (секрет KC_DEMO_*_TOTP из .env). Запуск из infra/: python3 keycloak/demo-qr.py senior

Ссылку превратить в QR любым генератором офлайн (например, `qrencode -t ansiutf8 '<ссылка>'`) и отсканировать
приложением-аутентификатором. Это секрет: не публиковать; после показа можно выдать новый код в разделе «Сотрудники».
"""
import base64
import sys
import urllib.parse
from pathlib import Path

KEYS = {"operator": "KC_DEMO_OPERATOR_TOTP", "senior": "KC_DEMO_SENIOR_TOTP", "auditor": "KC_DEMO_AUDITOR_TOTP"}
login = sys.argv[1] if len(sys.argv) > 1 else "senior"
env = dict(l.split("=", 1) for l in Path(".env").read_text().splitlines() if "=" in l and not l.startswith("#"))
secret = base64.b32encode(env[KEYS[login]].encode()).decode().rstrip("=")
issuer = urllib.parse.quote("Документы")
print(f"otpauth://totp/{issuer}:{login}?secret={secret}&issuer={issuer}&algorithm=SHA1&digits=6&period=30")
