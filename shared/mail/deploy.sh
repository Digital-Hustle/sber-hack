#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${SHARED_MAIL_ENV_FILE:-/etc/digital-hustle/mail.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  install -d -m 700 "$(dirname "$ENV_FILE")"
  umask 077
  cp "$ROOT/shared/mail/.env.example" "$ENV_FILE"
  TOKEN="$(openssl rand -hex 32)"
  sed -i "s/^SHARED_MAIL_TOKEN=$/SHARED_MAIL_TOKEN=$TOKEN/" "$ENV_FILE"
fi
chmod 600 "$ENV_FILE"
SHARED_MAIL_ENV_FILE="$ENV_FILE" docker compose -f "$ROOT/shared/mail/compose.yml" up -d --build
echo 'Почтовый клиент запущен; без SMTP_HOST отправка возвращает 503.'
