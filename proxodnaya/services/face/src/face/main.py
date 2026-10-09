"""Сборка приложения face: модели, БД, проверка токенов. ``uvicorn face.main:app --factory``."""

import logging
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI
from psycopg_pool import ConnectionPool

from face.api.routes import router
from face.core import errors
from face.core.config import Settings, get_settings
from face.core.security import TokenVerifier
from face.domain.service import FaceService
from face.infra.crypto import TemplateCipher
from face.infra.migrate import migrate
from face.infra.models import SFaceEmbedder, YuNetDetector, warmup
from face.infra.repository import PostgresTemplateStore


def create_app(
    service: FaceService,
    verifier: TokenVerifier,
    settings: Settings,
    ready: Callable[[], bool] | None = None,
    lifespan: Callable[[FastAPI], AsyncIterator[None]] | None = None,
) -> FastAPI:
    """Приложение из готовых зависимостей (тесты подставляют фейки)."""
    app = FastAPI(title="face", version="0.1.0", lifespan=asynccontextmanager(lifespan) if lifespan else None)
    app.state.service = service
    app.state.verifier = verifier
    app.state.settings = settings
    app.state.ready = ready
    errors.install(app)
    app.include_router(router)
    return app


def app() -> FastAPI:
    """Боевая сборка: миграции, пул БД, модели с прогревом, JWKS Keycloak."""
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    settings = get_settings()
    migrate(settings.dsn)
    pool = ConnectionPool(settings.dsn, min_size=1, max_size=8, open=True)
    store = PostgresTemplateStore(pool, TemplateCipher(settings.biometry_key.get_secret_value()))
    detector = YuNetDetector(settings.models_dir, settings.detect_score)
    embedder = SFaceEmbedder(settings.models_dir)
    warmup(detector, embedder)
    verifier = TokenVerifier.from_jwks(settings.oidc_issuer_uri, settings.audience, settings.jwks_uri)

    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        yield
        pool.close()

    return create_app(FaceService(detector, embedder, store, settings), verifier, settings, store.ping, lifespan)
