"""Шифрование эмбеддингов AES-256-GCM; AAD = workerId, чтобы эталон нельзя было переложить другому."""

import base64
import os

import numpy as np
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from face.domain.model import Vector

_KEY_BYTES = 32
_NONCE_BYTES = 12


class TemplateCipher:
    """Шифр эталонов."""

    def __init__(self, key_b64: str) -> None:
        key = base64.b64decode(key_b64)
        if len(key) != _KEY_BYTES:
            raise ValueError("BIOMETRY_KEY должен быть 32 байта в base64")
        self._aead = AESGCM(key)

    def encrypt(self, worker_id: str, vector: Vector) -> tuple[bytes, bytes]:
        """(шифртекст, nonce)."""
        nonce = os.urandom(_NONCE_BYTES)
        data = np.asarray(vector, dtype="<f4").tobytes()
        return self._aead.encrypt(nonce, data, worker_id.encode()), nonce

    def decrypt(self, worker_id: str, ciphertext: bytes, nonce: bytes) -> Vector:
        """Эмбеддинг; чужой workerId или порча — InvalidTag."""
        data = self._aead.decrypt(nonce, ciphertext, worker_id.encode())
        result: Vector = np.frombuffer(data, dtype="<f4").astype(np.float32)
        return result
