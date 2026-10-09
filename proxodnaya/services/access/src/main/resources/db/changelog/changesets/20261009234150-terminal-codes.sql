--liquibase formatted sql

--changeset miroslavrov:20261009234150-terminal-codes
-- требование: FR-64/14

CREATE TABLE terminal_codes (
    kind TEXT PRIMARY KEY CHECK (kind IN ('service', 'guard')),
    salt TEXT NOT NULL,
    hash TEXT NOT NULL,
    iterations INTEGER NOT NULL CHECK (iterations >= 120000),
    digits INTEGER NOT NULL CHECK (digits BETWEEN 4 AND 6),
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by TEXT NOT NULL
);

--rollback DROP TABLE terminal_codes;
