"""Типы домена и порты к моделям и хранилищу."""

from dataclasses import dataclass
from enum import StrEnum
from typing import Protocol

import numpy as np
import numpy.typing as npt

Image = npt.NDArray[np.uint8]
Vector = npt.NDArray[np.float32]


@dataclass(frozen=True)
class Detection:
    """Лицо на кадре: рамка, 5 точек YuNet (правый глаз, левый глаз, нос, правый и левый угол рта), уверенность.

    ``raw`` — исходная строка YuNet (15 чисел), её требует выравнивание SFace.
    """

    x: float
    y: float
    width: float
    height: float
    landmarks: tuple[tuple[float, float], ...]
    score: float
    raw: npt.NDArray[np.float32]


class Challenge(StrEnum):
    """Действие челленджа живости (как ChallengeAction в access)."""

    TURN_LEFT = "TURN_LEFT"
    TURN_RIGHT = "TURN_RIGHT"
    NOD = "NOD"
    BLINK = "BLINK"


class Reason(StrEnum):
    """Причины отказа — коды docs/API.md."""

    FACE_NOT_FOUND = "FACE_NOT_FOUND"
    FACE_MULTIPLE = "FACE_MULTIPLE"
    FACE_LOW_QUALITY = "FACE_LOW_QUALITY"
    FACE_NOT_ENROLLED = "FACE_NOT_ENROLLED"
    FACE_MISMATCH = "FACE_MISMATCH"
    LIVENESS_FAILED = "LIVENESS_FAILED"


class QualityCode(StrEnum):
    """Почему кадр не годится (для подсказки человеку)."""

    OK = "OK"
    NO_FACE = "NO_FACE"
    MULTIPLE = "MULTIPLE"
    TOO_SMALL = "TOO_SMALL"
    TOO_DARK = "TOO_DARK"
    TOO_BRIGHT = "TOO_BRIGHT"
    BLURRY = "BLURRY"


@dataclass(frozen=True)
class Quality:
    """Оценка кадра: 0..1 (минимум из размера, освещения, резкости) и код."""

    score: float
    code: QualityCode


@dataclass(frozen=True)
class VerifyResult:
    """Факты сверки для access: совпало ли, похожесть, живость, качество, причина отказа."""

    match: bool
    score: float
    liveness: bool
    quality: float
    reason: Reason | None


@dataclass(frozen=True)
class CheckResult:
    """Годится ли снимок как эталон."""

    ok: bool
    code: QualityCode
    quality: float


@dataclass(frozen=True)
class EnrollResult:
    """Итог заведения эталона."""

    accepted: int
    quality: float
    templates: int


@dataclass(frozen=True)
class StoredTemplate:
    """Эталон рабочего (расшифрованный эмбеддинг живёт только в памяти запроса)."""

    embedding: Vector
    model_version: str


class Detector(Protocol):
    """Детектор лиц."""

    def detect(self, image: Image) -> list[Detection]:
        """Все лица на кадре с уверенностью не ниже порога."""
        ...


class Embedder(Protocol):
    """Эмбеддинг лица, L2-нормированный."""

    version: str

    def embed(self, image: Image, face: Detection) -> Vector:
        """128-мерный вектор выровненного лица."""
        ...


class TemplateStore(Protocol):
    """Хранилище эталонов (зашифрованных)."""

    def load(self, worker_id: str) -> list[StoredTemplate]:
        """Эталоны рабочего."""
        ...

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
        """Сохраняет эталоны, возвращает сколько их теперь у рабочего."""
        ...

    def delete(self, worker_id: str) -> int:
        """Удаляет все эталоны рабочего."""
        ...
