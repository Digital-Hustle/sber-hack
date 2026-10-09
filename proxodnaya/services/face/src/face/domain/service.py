"""Сценарии face: сверка 1:1 с живостью, заведение эталона, проверка снимка, удаление эталонов."""

from dataclasses import dataclass

from face.core.config import Settings
from face.core.errors import DomainError
from face.domain import liveness, matcher, quality
from face.domain.model import (
    Challenge,
    CheckResult,
    Detection,
    Detector,
    Embedder,
    EnrollResult,
    Image,
    Quality,
    QualityCode,
    Reason,
    TemplateStore,
    VerifyResult,
)

MAX_ENROLL_FRAMES = 3


@dataclass(frozen=True)
class _Frame:
    image: Image
    face: Detection
    quality: Quality


class FaceService:
    """Конвейер: детекция → качество → эмбеддинг → сравнение → живость. Фото не сохраняются."""

    def __init__(self, detector: Detector, embedder: Embedder, store: TemplateStore, settings: Settings) -> None:
        self._detector = detector
        self._embedder = embedder
        self._store = store
        self._settings = settings

    def verify(self, worker_id: str, images: list[Image], challenge: Challenge) -> VerifyResult:
        """Сверяет кадры с эталонами рабочего. Отказ — через ``reason``, а не исключение."""
        frames: list[_Frame] = []
        for image in images:
            faces = self._detector.detect(image)
            if not faces:
                return _reject(Reason.FACE_NOT_FOUND)
            if len(faces) > 1:
                return _reject(Reason.FACE_MULTIPLE)
            frame_quality = quality.assess(image, faces[0], self._settings)
            if frame_quality.code is not QualityCode.OK:
                return _reject(Reason.FACE_LOW_QUALITY, quality=frame_quality.score)
            frames.append(_Frame(image, faces[0], frame_quality))
        worst_quality = min(frame.quality.score for frame in frames)

        templates = self._store.load(worker_id)
        current = [template.embedding for template in templates if template.model_version == self._embedder.version]
        if not current:
            return _reject(Reason.FACE_NOT_ENROLLED, quality=worst_quality)

        vectors = [self._embedder.embed(frame.image, frame.face) for frame in frames]
        score = round(matcher.best(vectors, current), 4)
        same_person = matcher.min_pairwise(vectors) >= self._settings.consistency_threshold
        match = same_person and score >= self._settings.match_threshold
        alive = liveness.passed(challenge, [frame.face for frame in frames], self._settings)
        reason = None
        if not match:
            reason = Reason.FACE_MISMATCH
        elif not alive:
            reason = Reason.LIVENESS_FAILED
        return VerifyResult(match=match, score=score, liveness=alive, quality=worst_quality, reason=reason)

    def check(self, image: Image) -> CheckResult:
        """Годится ли снимок эталоном: ровно одно лицо нужного качества."""
        faces = self._detector.detect(image)
        if not faces:
            return CheckResult(ok=False, code=QualityCode.NO_FACE, quality=0.0)
        if len(faces) > 1:
            return CheckResult(ok=False, code=QualityCode.MULTIPLE, quality=0.0)
        result = quality.assess(image, faces[0], self._settings)
        return CheckResult(ok=result.code is QualityCode.OK, code=result.code, quality=result.score)

    def enroll(self, worker_id: str, images: list[Image], replace: bool, actor: str) -> EnrollResult:
        """Заводит эталоны из 1..3 снимков. Плохой снимок — 422 с кодом качества, ничего не сохраняется."""
        if not 1 <= len(images) <= MAX_ENROLL_FRAMES:
            raise DomainError("VALIDATION_ERROR", f"Нужно от 1 до {MAX_ENROLL_FRAMES} снимков", 400)
        vectors = []
        scores = []
        for image in images:
            faces = self._detector.detect(image)
            code = QualityCode.NO_FACE if not faces else QualityCode.MULTIPLE if len(faces) > 1 else None
            if code is None:
                result = quality.assess(image, faces[0], self._settings)
                if result.code is not QualityCode.OK:
                    code = result.code
            if code is not None:
                raise DomainError("FACE_LOW_QUALITY", _HINTS[code], 422, [{"field": "frames", "code": code.value}])
            vectors.append(self._embedder.embed(image, faces[0]))
            scores.append(result.score)
        if matcher.min_pairwise(vectors) < self._settings.match_threshold:
            raise DomainError(
                "FACE_MULTIPLE", "На снимках разные люди", 422, [{"field": "frames", "code": "INCONSISTENT"}]
            )
        worst = min(scores)
        total = self._store.save(
            worker_id, vectors, worst, self._embedder.version, actor, replace, self._settings.max_templates
        )
        return EnrollResult(accepted=len(vectors), quality=worst, templates=total)

    def delete(self, worker_id: str) -> int:
        """Удаляет все эталоны рабочего (NFR-10)."""
        return self._store.delete(worker_id)


_HINTS = {
    QualityCode.NO_FACE: "Лицо не найдено — встаньте напротив камеры",
    QualityCode.MULTIPLE: "В кадре несколько человек — нужен один",
    QualityCode.TOO_SMALL: "Лицо слишком маленькое — подойдите ближе",
    QualityCode.TOO_DARK: "Слишком темно — добавьте света",
    QualityCode.TOO_BRIGHT: "Слишком ярко — уберите засветку",
    QualityCode.BLURRY: "Снимок смазан — держите камеру неподвижно",
    QualityCode.OK: "Снимок подходит",
}


def hint(code: QualityCode) -> str:
    """Подсказка человеку по коду качества."""
    return _HINTS[code]


def _reject(reason: Reason, quality: float = 0.0) -> VerifyResult:
    return VerifyResult(match=False, score=0.0, liveness=False, quality=quality, reason=reason)
