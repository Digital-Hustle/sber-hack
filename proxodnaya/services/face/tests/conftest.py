"""Общие фикстуры: настройки, фейковые модели, RSA-ключ для токенов."""

import base64
import time
from typing import Any

import jwt
import numpy as np
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from face.core.config import Settings
from face.core.security import TokenVerifier
from face.domain.model import Detection, Image, StoredTemplate, Vector

ISSUER = "http://keycloak:8080/realms/proxodnaya"
KEY = base64.b64encode(bytes(range(32))).decode()


def make_settings(**overrides: Any) -> Settings:
    """Настройки без окружения."""
    values: dict[str, Any] = {
        "BIOMETRY_KEY": KEY,
        "DB_HOST": "localhost",
        "FACE_DB_PASSWORD": "face",
        "OIDC_ISSUER_URI": ISSUER,
    }
    values.update(overrides)
    return Settings(**values)


@pytest.fixture
def settings() -> Settings:
    return make_settings()


def face_at(
    x: float = 100,
    y: float = 80,
    size: float = 160,
    nose_shift: float = 0.0,
    nose_drop: float = 0.5,
    score: float = 0.95,
) -> Detection:
    """Синтетическое лицо: глаза на 1/3 высоты, рот на 3/4, нос между ними."""
    right_eye = (x + size * 0.3, y + size * 0.35)
    left_eye = (x + size * 0.7, y + size * 0.35)
    eyes = left_eye[0] - right_eye[0]
    mouth_y = y + size * 0.75
    nose = (x + size * 0.5 + nose_shift * eyes, right_eye[1] + nose_drop * (mouth_y - right_eye[1]))
    points = (right_eye, left_eye, nose, (x + size * 0.35, mouth_y), (x + size * 0.65, mouth_y))
    raw = np.array([x, y, size, size, *[c for point in points for c in point], score], dtype=np.float32)
    return Detection(x, y, size, size, points, score, raw)


def textured(height: int = 480, width: int = 640, mean: int = 128, seed: int = 1) -> Image:
    """Кадр с шумом (резкий) заданной средней яркости."""
    rng = np.random.default_rng(seed)
    noise = rng.integers(-40, 40, size=(height, width, 3))
    return np.clip(mean + noise, 0, 255).astype(np.uint8)


class FakeDetector:
    """Отдаёт заранее заданные лица по порядку вызовов."""

    def __init__(self, *frames: list[Detection]) -> None:
        self._frames = list(frames)
        self.calls = 0

    def detect(self, image: Image) -> list[Detection]:
        faces = self._frames[min(self.calls, len(self._frames) - 1)]
        self.calls += 1
        return faces


class FakeEmbedder:
    """Эмбеддинг — заданные векторы по порядку."""

    version = "fake-1"

    def __init__(self, *vectors: Vector) -> None:
        self._vectors = list(vectors)
        self.calls = 0

    def embed(self, image: Image, face: Detection) -> Vector:
        vector = self._vectors[min(self.calls, len(self._vectors) - 1)]
        self.calls += 1
        return vector


class MemoryStore:
    """Хранилище эталонов в памяти."""

    def __init__(self) -> None:
        self.data: dict[str, list[StoredTemplate]] = {}

    def load(self, worker_id: str) -> list[StoredTemplate]:
        return list(self.data.get(worker_id, []))

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
        current = [] if replace else self.data.get(worker_id, [])
        current = [StoredTemplate(vector, model_version) for vector in embeddings] + current
        self.data[worker_id] = current[:keep]
        return len(self.data[worker_id])

    def delete(self, worker_id: str) -> int:
        return len(self.data.pop(worker_id, []))


def unit(*values: float) -> Vector:
    vector = np.array(values, dtype=np.float32)
    result: Vector = vector / np.linalg.norm(vector)
    return result


@pytest.fixture(scope="session")
def rsa_key() -> rsa.RSAPrivateKey:
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


@pytest.fixture
def verifier(rsa_key: rsa.RSAPrivateKey) -> TokenVerifier:
    public = rsa_key.public_key()
    return TokenVerifier(ISSUER, "face", lambda _: public)


@pytest.fixture
def token(rsa_key: rsa.RSAPrivateKey) -> Any:
    def issue(roles: list[str], audience: str = "face", issuer: str = ISSUER, ttl: int = 300) -> str:
        now = int(time.time())
        claims = {
            "iss": issuer,
            "aud": audience,
            "sub": "service-account-svc",
            "iat": now,
            "exp": now + ttl,
            "realm_access": {"roles": roles},
        }
        return jwt.encode(claims, rsa_key, algorithm="RS256")

    return issue
