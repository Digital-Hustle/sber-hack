"""Декодирование кадров: JPEG/PNG в base64 (допускается data-URL)."""

import base64
import binascii

import cv2
import numpy as np

from face.core.errors import DomainError
from face.domain.model import Image

_MAX_SIDE = 1920


def decode(encoded: str, max_bytes: int) -> Image:
    """base64 → BGR-изображение. Слишком большой кадр — 413, битый — 400 FRAME_INVALID."""
    payload = encoded.split(",", 1)[1] if encoded.startswith("data:") else encoded
    if len(payload) * 3 // 4 > max_bytes:
        raise DomainError("PAYLOAD_TOO_LARGE", "Кадр слишком большой", 413)
    try:
        raw = base64.b64decode(payload, validate=True)
    except (binascii.Error, ValueError) as error:
        raise DomainError("FRAME_INVALID", "Кадр не похож на base64-изображение", 400) from error
    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR) if raw else None
    if image is None:
        raise DomainError("FRAME_INVALID", "Не удалось прочитать изображение", 400)
    height, width = image.shape[:2]
    if max(height, width) > _MAX_SIDE:
        scale = _MAX_SIDE / max(height, width)
        image = cv2.resize(image, (int(width * scale), int(height * scale)))
    result: Image = np.asarray(image, dtype=np.uint8)
    return result


def encode(image: Image) -> str:
    """BGR → base64 JPEG (для тестов и отладки)."""
    ok, buffer = cv2.imencode(".jpg", image)
    if not ok:
        raise ValueError("JPEG encode failed")
    return base64.b64encode(buffer.tobytes()).decode("ascii")
