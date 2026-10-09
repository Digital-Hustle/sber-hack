"""HTTP API face: внутренние вызовы сервисов (service-токен) и health."""

import logging
import time
from typing import Annotated

from fastapi import APIRouter, Depends, Request, Response
from fastapi.responses import JSONResponse, PlainTextResponse
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from face.api import metrics
from face.api.schemas import CheckRequest, CheckResponse, EnrollRequest, EnrollResponse, VerifyRequest, VerifyResponse
from face.core.config import Settings
from face.core.errors import DomainError
from face.core.security import Caller, require_role
from face.domain.service import FaceService, hint
from face.infra import images

SVC_ACCESS = "SVC_ACCESS"
SVC_PEOPLE = "SVC_PEOPLE"

log = logging.getLogger("face")
router = APIRouter()


def get_service(request: Request) -> FaceService:
    """Сервис из состояния приложения."""
    service: FaceService = request.app.state.service
    return service


def get_settings(request: Request) -> Settings:
    """Настройки из состояния приложения."""
    settings: Settings = request.app.state.settings
    return settings


Service = Annotated[FaceService, Depends(get_service)]
Config = Annotated[Settings, Depends(get_settings)]


@router.post("/internal/face/verify", response_model=VerifyResponse, response_model_exclude_none=True)
def verify(
    body: VerifyRequest, service: Service, settings: Config, _: Annotated[Caller, Depends(require_role(SVC_ACCESS))]
) -> VerifyResponse:
    """Сверка 1:1 с эталоном владельца QR и проверка живости."""
    started = time.perf_counter()
    frames = [images.decode(frame, settings.max_frame_bytes) for frame in body.frames]
    result = service.verify(body.worker_id, frames, body.challenge)
    elapsed = time.perf_counter() - started
    metrics.LATENCY.labels("verify").observe(elapsed)
    metrics.RESULTS.labels(result.reason.value if result.reason else "OK").inc()
    if result.score > 0:
        metrics.FACE_SCORE.observe(result.score)
    log.info(
        "verify score=%.3f match=%s liveness=%s reason=%s latency_ms=%d",
        result.score,
        result.match,
        result.liveness,
        result.reason,
        elapsed * 1000,
    )
    return VerifyResponse(
        match=result.match, score=result.score, liveness=result.liveness, quality=result.quality, reason=result.reason
    )


@router.post("/internal/face/enroll", response_model=EnrollResponse)
def enroll(
    body: EnrollRequest,
    service: Service,
    settings: Config,
    caller: Annotated[Caller, Depends(require_role(SVC_PEOPLE))],
) -> EnrollResponse:
    """Заводит эталоны рабочего."""
    started = time.perf_counter()
    frames = [images.decode(frame, settings.max_frame_bytes) for frame in body.frames]
    result = service.enroll(body.worker_id, frames, body.replace, caller.subject)
    metrics.LATENCY.labels("enroll").observe(time.perf_counter() - started)
    return EnrollResponse(accepted=result.accepted, quality=result.quality, templates=result.templates)


@router.post("/internal/face/check", response_model=CheckResponse)
def check(
    body: CheckRequest, service: Service, settings: Config, _: Annotated[Caller, Depends(require_role(SVC_PEOPLE))]
) -> CheckResponse:
    """Проверяет снимок до сохранения карточки."""
    result = service.check(images.decode(body.photo, settings.max_frame_bytes))
    return CheckResponse(ok=result.ok, code=result.code, quality=result.quality, message=hint(result.code))


@router.delete("/internal/face/workers/{worker_id}", status_code=204)
def delete(worker_id: str, service: Service, _: Annotated[Caller, Depends(require_role(SVC_PEOPLE))]) -> Response:
    """Удаляет эталоны рабочего."""
    if not worker_id or len(worker_id) > 64:
        raise DomainError("VALIDATION_ERROR", "Неверный workerId", 400)
    removed = service.delete(worker_id)
    log.info("deleted templates count=%d", removed)
    return Response(status_code=204)


@router.get("/health/live", include_in_schema=False)
def live() -> dict[str, str]:
    """Процесс жив."""
    return {"status": "UP"}


@router.get("/health/ready", include_in_schema=False)
def ready(request: Request) -> JSONResponse:
    """Модели загружены и БД отвечает."""
    probe = getattr(request.app.state, "ready", None)
    up = bool(probe()) if callable(probe) else True
    return JSONResponse({"status": "UP" if up else "DOWN"}, status_code=200 if up else 503)


@router.get("/metrics", include_in_schema=False)
def prometheus() -> PlainTextResponse:
    """Метрики Prometheus."""
    return PlainTextResponse(generate_latest().decode(), media_type=CONTENT_TYPE_LATEST)
