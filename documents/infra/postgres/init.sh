#!/usr/bin/env bash
# Одна БД и одна роль на сервис (ADR-001) + БД Keycloak. Выполняется один раз при создании volume.
set -euo pipefail
create() {
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" <<SQL
create role $1 login password '$2';
create database $1 owner $1;
SQL
}
create intake "$INTAKE_DB_PASSWORD"
create docproc "$DOCPROC_DB_PASSWORD"
create review "$REVIEW_DB_PASSWORD"
create keycloak "$KC_DB_PASSWORD"
