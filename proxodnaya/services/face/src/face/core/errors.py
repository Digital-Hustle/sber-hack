"""Ошибки API в формате application/problem+json с кодом из docs/API.md."""

from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

PROBLEM = "application/problem+json"


class DomainError(Exception):
    """Ошибка с кодом и русским текстом для человека."""

    def __init__(self, code: str, detail: str, status: int = 400, errors: list[dict[str, str]] | None = None) -> None:
        super().__init__(detail)
        self.code = code
        self.detail = detail
        self.status = status
        self.errors = errors


def problem(status: int, code: str, detail: str, errors: list[dict[str, str]] | None = None) -> JSONResponse:
    """Ответ problem+json."""
    body: dict[str, Any] = {"type": "about:blank", "title": code, "status": status, "detail": detail, "code": code}
    if errors:
        body["errors"] = errors
    return JSONResponse(status_code=status, content=body, media_type=PROBLEM)


def install(app: FastAPI) -> None:
    """Подключает обработчики: домен, валидация, HTTP. Stack trace наружу не уходит."""

    @app.exception_handler(DomainError)
    async def domain(_: Request, error: DomainError) -> JSONResponse:
        return problem(error.status, error.code, error.detail, error.errors)

    @app.exception_handler(RequestValidationError)
    async def validation(_: Request, error: RequestValidationError) -> JSONResponse:
        fields = [
            {
                "field": ".".join(str(part) for part in item.get("loc", ()) if part != "body"),
                "code": str(item.get("type", "invalid")),
                "message": str(item.get("msg", "")),
            }
            for item in error.errors()
        ]
        return problem(400, "VALIDATION_ERROR", "Проверьте заполнение полей", fields)

    @app.exception_handler(StarletteHTTPException)
    async def http(_: Request, error: StarletteHTTPException) -> JSONResponse:
        codes = {
            401: "UNAUTHORIZED",
            403: "FORBIDDEN",
            404: "NOT_FOUND",
            405: "METHOD_NOT_ALLOWED",
            413: "PAYLOAD_TOO_LARGE",
        }
        return problem(error.status_code, codes.get(error.status_code, "ERROR"), str(error.detail))

    @app.exception_handler(Exception)
    async def unexpected(_: Request, __: Exception) -> JSONResponse:
        return problem(500, "SYSTEM_ERROR", "Внутренняя ошибка сервиса face")
