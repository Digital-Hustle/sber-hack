"""Эталоны в PostgreSQL (зашифрованные)."""

import uuid

from psycopg_pool import ConnectionPool

from face.domain.model import StoredTemplate, Vector
from face.infra.crypto import TemplateCipher


class PostgresTemplateStore:
    """Таблица face_templates; у рабочего хранится не больше ``keep`` свежих эталонов."""

    def __init__(self, pool: ConnectionPool, cipher: TemplateCipher) -> None:
        self._pool = pool
        self._cipher = cipher

    def load(self, worker_id: str) -> list[StoredTemplate]:
        """Расшифрованные эталоны рабочего."""
        with self._pool.connection() as connection:
            rows = connection.execute(
                "SELECT embedding_enc, nonce, model_version FROM face_templates WHERE worker_id = %s "
                "ORDER BY created_at DESC",
                (worker_id,),
            ).fetchall()
        return [
            StoredTemplate(self._cipher.decrypt(worker_id, bytes(row[0]), bytes(row[1])), _text(row[2])) for row in rows
        ]

    def save(
        self,
        worker_id: str,
        embeddings: list[Vector],
        quality: float,
        model_version: str,
        actor: str,
        replace: bool,
        keep: int,
    ) -> int:
        """Сохраняет эталоны и обрезает старые; возвращает число эталонов рабочего."""
        with self._pool.connection() as connection, connection.transaction():
            connection.execute("SELECT pg_advisory_xact_lock(hashtext(%s))", (worker_id,))
            if replace:
                connection.execute("DELETE FROM face_templates WHERE worker_id = %s", (worker_id,))
            for vector in embeddings:
                ciphertext, nonce = self._cipher.encrypt(worker_id, vector)
                connection.execute(
                    "INSERT INTO face_templates (id, worker_id, embedding_enc, nonce, model_version, quality, "
                    "created_by, created_at) VALUES (%s, %s, %s, %s, %s, %s, %s, clock_timestamp())",
                    (uuid.uuid4(), worker_id, ciphertext, nonce, model_version, quality, actor),
                )
            connection.execute(
                "DELETE FROM face_templates WHERE worker_id = %s AND id NOT IN (SELECT id FROM face_templates "
                "WHERE worker_id = %s ORDER BY created_at DESC LIMIT %s)",
                (worker_id, worker_id, keep),
            )
            row = connection.execute(
                "SELECT count(*) FROM face_templates WHERE worker_id = %s", (worker_id,)
            ).fetchone()
        return int(row[0]) if row else 0

    def delete(self, worker_id: str) -> int:
        """Удаляет эталоны рабочего."""
        with self._pool.connection() as connection:
            cursor = connection.execute("DELETE FROM face_templates WHERE worker_id = %s", (worker_id,))
            return cursor.rowcount

    def ping(self) -> bool:
        """БД отвечает."""
        try:
            with self._pool.connection(timeout=2) as connection:
                connection.execute("SELECT 1")
        except Exception:
            return False
        return True


def _text(value: str | bytes) -> str:
    return value.decode() if isinstance(value, bytes) else value
