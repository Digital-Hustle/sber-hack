"""Фикстуры с настоящими моделями (скачиваются с проверкой sha256) и своей временной БД."""

import os
import urllib.request
import uuid
from collections.abc import Iterator
from pathlib import Path

import cv2
import psycopg
import pytest
from psycopg import sql

from face.domain.model import Image
from face.infra.fetch import fetch
from face.infra.models import MODELS, SFaceEmbedder, YuNetDetector

CACHE = Path(__file__).resolve().parents[2] / ".models"
SAMPLES = "https://raw.githubusercontent.com/opencv/opencv/4.x/samples/data"


@pytest.fixture(scope="session")
def models_dir() -> Path:
    directory = Path(os.environ.get("MODELS_DIR", CACHE))
    try:
        for model in MODELS:
            fetch(directory, model)
    except OSError as error:
        pytest.skip(f"модели недоступны: {error}")
    return directory


@pytest.fixture(scope="session")
def detector(models_dir: Path) -> YuNetDetector:
    return YuNetDetector(models_dir, 0.85)


@pytest.fixture(scope="session")
def embedder(models_dir: Path) -> SFaceEmbedder:
    return SFaceEmbedder(models_dir)


def sample(name: str) -> Image:
    """Тестовое фото из samples OpenCV (кэшируется рядом с моделями, в git не попадает)."""
    path = CACHE / "samples" / name
    if not path.is_file():
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            urllib.request.urlretrieve(f"{SAMPLES}/{name}", path)  # noqa: S310
        except OSError as error:
            pytest.skip(f"образец {name} недоступен: {error}")
    image: Image = cv2.imread(str(path))
    return image


@pytest.fixture(scope="session")
def lena() -> Image:
    return sample("lena.jpg")


@pytest.fixture(scope="session")
def messi() -> Image:
    image = sample("messi5.jpg")
    result: Image = cv2.resize(image, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
    return result


@pytest.fixture(scope="session")
def database() -> Iterator[str]:
    """Создаёт пустую БД под тесты через админа PostgreSQL (DB_HOST, DB_ADMIN_USER/PASSWORD)."""
    host = os.environ.get("DB_HOST")
    if not host:
        pytest.skip("DB_HOST не задан")
    port = os.environ.get("DB_PORT", "5432")
    user = os.environ.get("DB_ADMIN_USER", "postgres")
    password = os.environ.get("DB_ADMIN_PASSWORD", "postgres")
    admin = f"host={host} port={port} dbname=postgres user={user} password={password}"
    name = f"face_test_{uuid.uuid4().hex[:8]}"
    try:
        with psycopg.connect(admin, autocommit=True) as connection:
            connection.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(name)))
    except psycopg.OperationalError as error:
        pytest.skip(f"PostgreSQL недоступен: {error}")
    yield f"host={host} port={port} dbname={name} user={user} password={password}"
    with psycopg.connect(admin, autocommit=True) as connection:
        connection.execute(sql.SQL("DROP DATABASE {} WITH (FORCE)").format(sql.Identifier(name)))
