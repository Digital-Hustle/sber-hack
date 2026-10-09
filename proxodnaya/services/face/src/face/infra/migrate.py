"""Миграции схемы: db/migrations/NNN_*.sql по порядку, учёт в schema_migrations, под advisory lock."""

from pathlib import Path

import psycopg

_LOCK = 4_242_001
MIGRATIONS = Path(__file__).resolve().parents[3] / "db" / "migrations"


def migrate(dsn: str, directory: Path = MIGRATIONS) -> list[str]:
    """Применяет новые миграции, возвращает их имена."""
    applied: list[str] = []
    with psycopg.connect(dsn, autocommit=True) as connection:
        connection.execute("SELECT pg_advisory_lock(%s)", (_LOCK,))
        try:
            connection.execute(
                "CREATE TABLE IF NOT EXISTS schema_migrations (name varchar(200) PRIMARY KEY, "
                "applied_at timestamptz NOT NULL DEFAULT now())"
            )
            done = {_text(row[0]) for row in connection.execute("SELECT name FROM schema_migrations")}
            for script in sorted(directory.glob("*.sql")):
                if script.name in done:
                    continue
                with connection.transaction():
                    connection.execute(script.read_text(encoding="utf-8").encode())
                    connection.execute("INSERT INTO schema_migrations (name) VALUES (%s)", (script.name,))
                applied.append(script.name)
        finally:
            connection.execute("SELECT pg_advisory_unlock(%s)", (_LOCK,))
    return applied


def _text(value: str | bytes) -> str:
    # на кластере SQL_ASCII psycopg отдаёт text как bytes
    return value.decode() if isinstance(value, bytes) else value
