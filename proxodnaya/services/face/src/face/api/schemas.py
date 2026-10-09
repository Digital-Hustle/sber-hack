"""Тела запросов и ответов /internal/face (docs/API.md, camelCase)."""

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from face.domain.model import Challenge, QualityCode, Reason

_WORKER_ID = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_.:-]+$")


class _Body(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")


class VerifyRequest(_Body):
    """Сверка: кадры JPEG base64 и действие челленджа."""

    worker_id: str = _WORKER_ID
    frames: list[str] = Field(min_length=1, max_length=5)
    challenge: Challenge


class VerifyResponse(_Body):
    """Факты для решения access."""

    match: bool
    score: float
    liveness: bool
    quality: float
    reason: Reason | None = None


class EnrollRequest(_Body):
    """Заведение эталона: 1..3 снимка."""

    worker_id: str = _WORKER_ID
    frames: list[str] = Field(min_length=1, max_length=3)
    replace: bool = True


class EnrollResponse(_Body):
    """Сколько снимков принято и сколько эталонов теперь у рабочего."""

    accepted: int
    quality: float
    templates: int


class CheckRequest(_Body):
    """Проверка снимка до сохранения."""

    photo: str = Field(min_length=1)


class CheckResponse(_Body):
    """Годится ли снимок и что исправить."""

    ok: bool
    code: QualityCode
    quality: float
    message: str
