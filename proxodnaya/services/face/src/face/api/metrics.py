"""Метрики Prometheus (FACE-09): похожесть и время сверки. Биометрию не пишем."""

from prometheus_client import Counter, Histogram

FACE_SCORE = Histogram(
    "face_score", "Косинус SFace лучшего кадра с эталоном", buckets=(0.1, 0.2, 0.3, 0.363, 0.4, 0.5, 0.6, 0.7, 0.8, 1.0)
)
LATENCY = Histogram("face_verify_seconds", "Время сверки", ["operation"])
RESULTS = Counter("face_verify_total", "Итоги сверки", ["reason"])
