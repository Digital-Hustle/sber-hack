"""Хранилище эталонов на настоящем PostgreSQL."""

import psycopg
import pytest
from psycopg_pool import ConnectionPool

from face.infra.crypto import TemplateCipher
from face.infra.migrate import migrate
from face.infra.repository import PostgresTemplateStore
from tests.conftest import KEY, unit

pytestmark = pytest.mark.db


def test_migrate_is_idempotent(database: str) -> None:
    assert migrate(database) == ["001_face_templates.sql"]
    assert migrate(database) == []


def test_save_load_trim_delete(database: str) -> None:
    migrate(database)
    with ConnectionPool(database, min_size=1, max_size=2, open=True) as pool:
        store = PostgresTemplateStore(pool, TemplateCipher(KEY))
        assert store.ping()
        assert store.save("w-1", [unit(1, 0), unit(0, 1)], 0.9, "v1", "svc-people", replace=True, keep=3) == 2
        assert store.save("w-1", [unit(1, 1), unit(1, 2)], 0.8, "v1", "svc-people", replace=False, keep=3) == 3
        loaded = store.load("w-1")
        assert len(loaded) == 3
        assert {template.model_version for template in loaded} == {"v1"}
        assert float(loaded[0].embedding @ unit(1, 2)) == pytest.approx(1.0)
        with psycopg.connect(database) as connection:
            raw = connection.execute("SELECT embedding_enc FROM face_templates LIMIT 1").fetchone()
        assert raw is not None
        assert unit(1, 0).tobytes() not in bytes(raw[0])
        assert store.load("w-2") == []
        assert store.save("w-1", [unit(3, 1)], 0.7, "v1", "svc-people", replace=True, keep=3) == 1
        assert store.delete("w-1") == 1
        assert store.load("w-1") == []
