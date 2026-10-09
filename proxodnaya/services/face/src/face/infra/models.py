"""Адаптеры моделей OpenCV Zoo: YuNet (детекция + 5 точек) и SFace (эмбеддинг 128-d)."""

import hashlib
import threading
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

from face.domain.model import Detection, Image, Vector


@dataclass(frozen=True)
class ModelFile:
    """Файл модели: имя, адрес загрузки и sha256."""

    name: str
    url: str
    sha256: str


_ZOO = "https://github.com/opencv/opencv_zoo/raw/main/models"
YUNET = ModelFile(
    "face_detection_yunet_2023mar.onnx",
    f"{_ZOO}/face_detection_yunet/face_detection_yunet_2023mar.onnx",
    "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
)
SFACE = ModelFile(
    "face_recognition_sface_2021dec.onnx",
    f"{_ZOO}/face_recognition_sface/face_recognition_sface_2021dec.onnx",
    "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79",
)
MODELS = (YUNET, SFACE)


def sha256(path: Path) -> str:
    """Хэш файла."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verified(models_dir: Path, model: ModelFile) -> Path:
    """Путь к модели, если файл на месте и sha256 совпал, иначе RuntimeError."""
    path = models_dir / model.name
    if not path.is_file():
        raise RuntimeError(f"Нет модели {path}: запустите scripts/fetch_models.sh")
    if sha256(path) != model.sha256:
        raise RuntimeError(f"sha256 модели {path} не совпадает")
    return path


class YuNetDetector:
    """cv2.FaceDetectorYN. Не потокобезопасен — вызовы под замком."""

    def __init__(self, models_dir: Path, score_threshold: float) -> None:
        self._net = cv2.FaceDetectorYN.create(
            str(verified(models_dir, YUNET)), "", (320, 320), score_threshold, 0.3, 50
        )
        self._lock = threading.Lock()

    def detect(self, image: Image) -> list[Detection]:
        """Лица на кадре."""
        height, width = image.shape[:2]
        with self._lock:
            self._net.setInputSize((width, height))
            _, faces = self._net.detect(image)
        if faces is None:
            return []
        result = []
        for row in faces:
            values = [float(value) for value in row]
            points = tuple((values[4 + 2 * index], values[5 + 2 * index]) for index in range(5))
            result.append(Detection(values[0], values[1], values[2], values[3], points, values[14], row.copy()))
        return result


class SFaceEmbedder:
    """cv2.FaceRecognizerSF: выравнивание по 5 точкам и признаки 128-d."""

    version = "sface-2021dec"

    def __init__(self, models_dir: Path) -> None:
        self._net = cv2.FaceRecognizerSF.create(str(verified(models_dir, SFACE)), "")
        self._lock = threading.Lock()

    def embed(self, image: Image, face: Detection) -> Vector:
        """L2-нормированный эмбеддинг."""
        with self._lock:
            aligned = self._net.alignCrop(image, face.raw)
            feature = self._net.feature(aligned)
        vector = np.asarray(feature, dtype=np.float32).reshape(-1)
        norm = float(np.linalg.norm(vector))
        result: Vector = vector / norm if norm > 0 else vector
        return result


def warmup(detector: YuNetDetector, embedder: SFaceEmbedder) -> None:
    """Первый прогон, чтобы первая сверка не платила за инициализацию."""
    blank = np.zeros((240, 320, 3), dtype=np.uint8)
    detector.detect(blank)
    fake = Detection(
        100,
        60,
        120,
        120,
        ((130, 100), (190, 100), (160, 130), (135, 160), (185, 160)),
        1.0,
        np.array([100, 60, 120, 120, 130, 100, 190, 100, 160, 130, 135, 160, 185, 160, 1.0], np.float32),
    )
    embedder.embed(blank, fake)
