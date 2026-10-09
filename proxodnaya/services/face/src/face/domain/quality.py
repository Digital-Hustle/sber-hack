"""Качество кадра: размер лица, освещённость и резкость лица (FR-05)."""

import cv2
import numpy as np

from face.core.config import Settings
from face.domain.model import Detection, Image, Quality, QualityCode

_CROP = 112


def assess(image: Image, face: Detection, settings: Settings) -> Quality:
    """Оценивает лицо на кадре. Код — первая проблема по порядку: размер, свет, резкость."""
    crop = _crop(image, face)
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY) if crop.ndim == 3 else crop
    brightness = float(np.mean(gray))
    sharpness = float(cv2.Laplacian(cv2.resize(gray, (_CROP, _CROP)), cv2.CV_64F).var())
    size = min(face.width, face.height)

    size_score = min(1.0, size / (settings.min_face_px * 1.5))
    light_score = max(0.0, 1.0 - abs(brightness - 128.0) / 128.0)
    sharp_score = min(1.0, sharpness / (settings.min_sharpness * 3.0))
    score = round(min(size_score, light_score, sharp_score), 3)

    code = QualityCode.OK
    if size < settings.min_face_px:
        code = QualityCode.TOO_SMALL
    elif brightness < settings.min_brightness:
        code = QualityCode.TOO_DARK
    elif brightness > settings.max_brightness:
        code = QualityCode.TOO_BRIGHT
    elif sharpness < settings.min_sharpness:
        code = QualityCode.BLURRY
    return Quality(score, code)


def _crop(image: Image, face: Detection) -> Image:
    height, width = image.shape[:2]
    left = max(0, int(face.x))
    top = max(0, int(face.y))
    right = min(width, int(face.x + face.width))
    bottom = min(height, int(face.y + face.height))
    if right <= left or bottom <= top:
        return image
    return image[top:bottom, left:right]
