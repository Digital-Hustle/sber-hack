import numpy as np

from face.core.config import Settings
from face.domain.model import QualityCode
from face.domain.quality import assess
from tests.conftest import face_at, textured


def test_good_frame(settings: Settings) -> None:
    result = assess(textured(), face_at(), settings)
    assert result.code is QualityCode.OK
    assert 0.5 < result.score <= 1.0


def test_small_face(settings: Settings) -> None:
    assert assess(textured(), face_at(size=60), settings).code is QualityCode.TOO_SMALL


def test_dark_and_bright(settings: Settings) -> None:
    assert assess(textured(mean=15), face_at(), settings).code is QualityCode.TOO_DARK
    assert assess(textured(mean=245), face_at(), settings).code is QualityCode.TOO_BRIGHT


def test_blurry(settings: Settings) -> None:
    flat = np.full((480, 640, 3), 128, dtype=np.uint8)
    result = assess(flat, face_at(), settings)
    assert result.code is QualityCode.BLURRY
    assert result.score < 0.1


def test_face_outside_frame_uses_whole_image(settings: Settings) -> None:
    assert assess(textured(), face_at(x=900, y=900), settings).code is QualityCode.OK
