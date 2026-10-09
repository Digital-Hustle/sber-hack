"""Живость по активному челленджу (FR-06): движение головы по 5 точкам YuNet между кадрами.

Фото перед камерой не меняет геометрию лица — поворот и кивок не получаются. Моргание по 5 точкам не видно,
поэтому BLINK честно не проходит (access его не выдаёт).
"""

from face.core.config import Settings
from face.domain.model import Challenge, Detection


def yaw(face: Detection) -> float:
    """Смещение носа от середины глаз в долях межглазного расстояния; > 0 — нос правее на кадре."""
    (right_eye_x, _), (left_eye_x, _), (nose_x, _) = face.landmarks[0], face.landmarks[1], face.landmarks[2]
    eyes = abs(left_eye_x - right_eye_x)
    if eyes < 1e-6:
        return 0.0
    return (nose_x - (right_eye_x + left_eye_x) / 2.0) / eyes


def pitch(face: Detection) -> float:
    """Положение носа между линией глаз (0) и линией рта (1)."""
    eye_y = (face.landmarks[0][1] + face.landmarks[1][1]) / 2.0
    mouth_y = (face.landmarks[3][1] + face.landmarks[4][1]) / 2.0
    span = mouth_y - eye_y
    if abs(span) < 1e-6:
        return 0.0
    return (face.landmarks[2][1] - eye_y) / span


def passed(challenge: Challenge, faces: list[Detection], settings: Settings) -> bool:
    """Выполнен ли челлендж на последовательности кадров (по одному лицу на кадр)."""
    if len(faces) < 2:
        return False
    if challenge is Challenge.NOD:
        values = [pitch(face) for face in faces]
        return max(values) - min(values) >= settings.pitch_delta
    if challenge in (Challenge.TURN_LEFT, Challenge.TURN_RIGHT):
        # человек поворачивается влево → на незеркальном кадре нос уходит вправо
        sign = 1.0 if challenge is Challenge.TURN_LEFT else -1.0
        if settings.mirrored:
            sign = -sign
        values = [sign * yaw(face) for face in faces]
        frontal = any(abs(value) <= settings.yaw_frontal for value in values)
        return frontal and max(values) >= settings.yaw_min
    return False
