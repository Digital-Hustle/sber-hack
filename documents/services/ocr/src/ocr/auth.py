"""Проверка service-account JWT Keycloak: подпись по JWKS, issuer, aud=ocr, роль SERVICE_DOCPROC."""

from __future__ import annotations

from functools import lru_cache
from typing import Any

import jwt
from fastapi import Header

from ocr.config import settings
from ocr.errors import OcrError


@lru_cache(maxsize=1)
def _jwks() -> jwt.PyJWKClient:
    return jwt.PyJWKClient(settings.jwks_uri, cache_keys=True, lifespan=300)


def verify(token: str) -> dict[str, Any]:
    if not settings.jwks_uri or not settings.oidc_issuer:
        raise OcrError("UNAUTHORIZED", 401, "Проверка токена не настроена")
    try:
        key = _jwks().get_signing_key_from_jwt(token)
        claims: dict[str, Any] = jwt.decode(
            token,
            key.key,
            algorithms=["RS256", "ES256", "PS256"],
            audience=settings.audience,
            issuer=settings.oidc_issuer,
            options={"require": ["exp", "iss"]},
        )
    except (jwt.PyJWTError, jwt.PyJWKClientError) as e:
        raise OcrError("UNAUTHORIZED", 401, "Неверный или просроченный токен") from e
    roles = claims.get("realm_access", {}).get("roles", [])
    if settings.required_role not in roles:
        raise OcrError("FORBIDDEN", 403, "Нет роли сервиса")
    return claims


def require_service(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    if not authorization or not authorization.startswith("Bearer "):
        raise OcrError("UNAUTHORIZED", 401, "Нужен токен сервиса")
    return verify(authorization.removeprefix("Bearer ").strip())
