"""Проверка service-токенов Keycloak: подпись по JWKS, issuer, audience, роли из realm_access.roles."""

from collections.abc import Callable
from dataclasses import dataclass
from typing import Annotated, Any

import jwt
from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from face.core.errors import DomainError

_bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class Caller:
    """Кто вызвал: subject токена и роли."""

    subject: str
    roles: frozenset[str]


class TokenVerifier:
    """Проверяет JWT. Ключ подписи берёт из JWKS (кэшируется PyJWKClient) или из переданного провайдера."""

    def __init__(self, issuer: str, audience: str, key_for: Callable[[str], Any]) -> None:
        self._issuer = issuer
        self._audience = audience
        self._key_for = key_for

    @classmethod
    def from_jwks(cls, issuer: str, audience: str, jwks_uri: str) -> "TokenVerifier":
        """Боевой режим: ключи Keycloak по JWKS."""
        client = jwt.PyJWKClient(jwks_uri, cache_keys=True, lifespan=300)
        return cls(issuer, audience, lambda token: client.get_signing_key_from_jwt(token).key)

    def verify(self, token: str) -> Caller:
        """Возвращает вызывающего или бросает 401."""
        try:
            claims = jwt.decode(
                token,
                self._key_for(token),
                algorithms=["RS256", "ES256"],
                audience=self._audience,
                issuer=self._issuer,
                options={"require": ["exp", "iss", "sub"]},
            )
        except (jwt.PyJWTError, ValueError) as error:
            raise DomainError("UNAUTHORIZED", "Нужен действующий токен сервиса", 401) from error
        realm = claims.get("realm_access") or {}
        roles = realm.get("roles") if isinstance(realm, dict) else None
        return Caller(
            str(claims["sub"]), frozenset(str(role) for role in roles) if isinstance(roles, list) else frozenset()
        )


def get_verifier(request: Request) -> TokenVerifier:
    """Проверяльщик из состояния приложения (в тестах подменяется)."""
    verifier: TokenVerifier = request.app.state.verifier
    return verifier


def require_role(*roles: str) -> Callable[..., Caller]:
    """Зависимость FastAPI: токен действителен и у вызывающего есть одна из ролей, иначе 401/403."""

    def dependency(
        verifier: Annotated[TokenVerifier, Depends(get_verifier)],
        credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
    ) -> Caller:
        if credentials is None:
            raise DomainError("UNAUTHORIZED", "Нужен токен сервиса", 401)
        caller = verifier.verify(credentials.credentials)
        if not caller.roles.intersection(roles):
            raise DomainError("FORBIDDEN", "Нет прав на этот вызов", 403)
        return caller

    return dependency
