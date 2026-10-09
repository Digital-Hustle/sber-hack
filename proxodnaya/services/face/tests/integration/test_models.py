"""Конвейер на настоящих YuNet + SFace."""

import cv2
import numpy as np
import pytest

from face.core.config import Settings
from face.domain.model import Challenge, Image, QualityCode, Reason
from face.domain.service import FaceService
from face.infra import images
from face.infra.models import SFaceEmbedder, YuNetDetector, warmup
from tests.conftest import MemoryStore

pytestmark = pytest.mark.models


@pytest.fixture
def service(detector: YuNetDetector, embedder: SFaceEmbedder, settings: Settings, lena: Image) -> FaceService:
    face = FaceService(detector, embedder, MemoryStore(), settings)
    face.enroll("w-1", [lena], replace=True, actor="test")
    return face


def test_same_person_matches_through_jpeg(service: FaceService, lena: Image) -> None:
    frames = [images.decode(images.encode(frame), 2_000_000) for frame in (cv2.resize(lena, (400, 400)), lena)]
    result = service.verify("w-1", frames, Challenge.TURN_LEFT)
    assert result.match
    assert result.score > 0.8
    # неподвижное фото не проходит живость
    assert not result.liveness
    assert result.reason is Reason.LIVENESS_FAILED


def test_other_person_mismatch(service: FaceService, messi: Image) -> None:
    result = service.verify("w-1", [messi], Challenge.NOD)
    assert not result.match
    assert result.score < 0.363
    assert result.reason is Reason.FACE_MISMATCH


def test_no_face_and_multiple(service: FaceService, lena: Image) -> None:
    noise = np.random.default_rng(7).integers(0, 255, size=(480, 640, 3), dtype=np.uint8)
    assert service.verify("w-1", [noise], Challenge.NOD).reason is Reason.FACE_NOT_FOUND
    assert service.verify("w-1", [np.hstack([lena, lena])], Challenge.NOD).reason is Reason.FACE_MULTIPLE


def test_check_quality(service: FaceService, lena: Image) -> None:
    assert service.check(lena).ok
    assert service.check(cv2.resize(lena, (120, 120))).code is QualityCode.TOO_SMALL
    assert service.check(cv2.GaussianBlur(lena, (31, 31), 12)).code in {QualityCode.BLURRY, QualityCode.NO_FACE}


def test_warmup(detector: YuNetDetector, embedder: SFaceEmbedder) -> None:
    warmup(detector, embedder)
