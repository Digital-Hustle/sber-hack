#!/usr/bin/env bash
# Одна БД и одна роль на сервис (ADR-017) + БД Keycloak. Выполняется один раз — при пустом томе postgres.
# Пароли приходят из окружения контейнера (infra/.env), в git их нет.
set -euo pipefail
create() {
  local name="$1" password="$2"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v name="$name" -v password="$password" <<'SQL'
CREATE ROLE :"name" LOGIN PASSWORD :'password';
CREATE DATABASE :"name" OWNER :"name";
REVOKE ALL ON DATABASE :"name" FROM PUBLIC;
SQL
}
create people "$PEOPLE_DB_PASSWORD"
create access "$ACCESS_DB_PASSWORD"
create shift "$SHIFT_DB_PASSWORD"
create face "$FACE_DB_PASSWORD"
create assistant "$ASSISTANT_DB_PASSWORD"
create keycloak "$KC_DB_PASSWORD"
