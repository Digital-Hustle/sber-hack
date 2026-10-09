"""Сравнение эмбеддингов: косинус L2-нормированных векторов SFace."""

import numpy as np

from face.domain.model import Vector


def cosine(left: Vector, right: Vector) -> float:
    """Косинусное сходство."""
    denominator = float(np.linalg.norm(left) * np.linalg.norm(right))
    if denominator == 0.0:
        return 0.0
    return float(np.dot(left, right) / denominator)


def min_pairwise(vectors: list[Vector]) -> float:
    """Минимальное сходство между любыми двумя векторами (1.0 для одного)."""
    worst = 1.0
    for index, left in enumerate(vectors):
        for right in vectors[index + 1 :]:
            worst = min(worst, cosine(left, right))
    return worst


def best(vectors: list[Vector], templates: list[Vector]) -> float:
    """Лучшее сходство кадров с эталонами."""
    return max((cosine(vector, template) for vector in vectors for template in templates), default=0.0)
