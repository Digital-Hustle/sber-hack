import pytest

from face.core.config import Settings
from face.core.errors import DomainError
from face.domain.model import Challenge, QualityCode, Reason, StoredTemplate
from face.domain.service import FaceService
from tests.conftest import FakeDetector, FakeEmbedder, MemoryStore, face_at, textured, unit

OWNER = unit(1, 0, 0)
SIMILAR = unit(1, 0.3, 0)
STRANGER = unit(0, 1, 0)
TURN = [[face_at()], [face_at(nose_shift=0.25)]]


def service(settings: Settings, detector: FakeDetector, embedder: FakeEmbedder, store: MemoryStore) -> FaceService:
    return FaceService(detector, embedder, store, settings)


def enrolled(*vectors: object, version: str = "fake-1") -> MemoryStore:
    store = MemoryStore()
    store.data["w-1"] = [StoredTemplate(vector, version) for vector in vectors]  # type: ignore[arg-type]
    return store


def frames(count: int = 2) -> list:  # type: ignore[type-arg]
    return [textured(seed=index) for index in range(count)]


def test_owner_turning_left_passes(settings: Settings) -> None:
    result = service(settings, FakeDetector(*TURN), FakeEmbedder(SIMILAR, OWNER), enrolled(OWNER)).verify(
        "w-1", frames(), Challenge.TURN_LEFT
    )
    assert result.match
    assert result.liveness
    assert result.reason is None
    assert result.score == pytest.approx(1.0)
    assert 0 < result.quality <= 1


def test_owner_without_motion_fails_liveness(settings: Settings) -> None:
    result = service(settings, FakeDetector([face_at()]), FakeEmbedder(OWNER), enrolled(OWNER)).verify(
        "w-1", frames(), Challenge.TURN_LEFT
    )
    assert result.match
    assert not result.liveness
    assert result.reason is Reason.LIVENESS_FAILED


def test_stranger_mismatch(settings: Settings) -> None:
    result = service(settings, FakeDetector(*TURN), FakeEmbedder(STRANGER), enrolled(OWNER)).verify(
        "w-1", frames(), Challenge.TURN_LEFT
    )
    assert not result.match
    assert result.reason is Reason.FACE_MISMATCH


def test_different_people_between_frames_is_mismatch(settings: Settings) -> None:
    result = service(settings, FakeDetector(*TURN), FakeEmbedder(OWNER, STRANGER), enrolled(OWNER)).verify(
        "w-1", frames(), Challenge.TURN_LEFT
    )
    assert not result.match
    assert result.reason is Reason.FACE_MISMATCH


@pytest.mark.parametrize(
    ("faces", "reason"),
    [([], Reason.FACE_NOT_FOUND), ([face_at(), face_at(x=300)], Reason.FACE_MULTIPLE)],
)
def test_detection_failures(settings: Settings, faces: list, reason: Reason) -> None:  # type: ignore[type-arg]
    result = service(settings, FakeDetector(faces), FakeEmbedder(OWNER), enrolled(OWNER)).verify(
        "w-1", frames(), Challenge.NOD
    )
    assert result.reason is reason
    assert not result.match


def test_low_quality(settings: Settings) -> None:
    result = service(settings, FakeDetector([face_at(size=50)]), FakeEmbedder(OWNER), enrolled(OWNER)).verify(
        "w-1", frames(), Challenge.NOD
    )
    assert result.reason is Reason.FACE_LOW_QUALITY


def test_not_enrolled_and_old_model_version(settings: Settings) -> None:
    embedder = FakeEmbedder(OWNER)
    assert (
        service(settings, FakeDetector(*TURN), embedder, MemoryStore())
        .verify("w-1", frames(), Challenge.TURN_LEFT)
        .reason
        is Reason.FACE_NOT_ENROLLED
    )
    assert (
        service(settings, FakeDetector(*TURN), embedder, enrolled(OWNER, version="old"))
        .verify("w-1", frames(), Challenge.TURN_LEFT)
        .reason
        is Reason.FACE_NOT_ENROLLED
    )
    assert embedder.calls == 0


def test_enroll_and_trim(settings: Settings) -> None:
    store = MemoryStore()
    face = service(settings, FakeDetector([face_at()]), FakeEmbedder(OWNER, SIMILAR), store)
    first = face.enroll("w-1", frames(2), replace=True, actor="svc-people")
    assert first.accepted == 2
    assert first.templates == 2
    assert face.enroll("w-1", frames(3), replace=False, actor="svc-people").templates == 5
    assert face.enroll("w-1", frames(1), replace=False, actor="svc-people").templates == 5
    assert face.enroll("w-1", frames(1), replace=True, actor="svc-people").templates == 1
    assert face.delete("w-1") == 1
    assert store.load("w-1") == []


def test_enroll_rejects_bad_photo_without_saving(settings: Settings) -> None:
    store = MemoryStore()
    face = service(settings, FakeDetector([face_at()], []), FakeEmbedder(OWNER), store)
    with pytest.raises(DomainError) as error:
        face.enroll("w-1", frames(2), replace=True, actor="svc-people")
    assert error.value.status == 422
    assert error.value.errors == [{"field": "frames", "code": "NO_FACE"}]
    assert store.data == {}


def test_enroll_rejects_different_people(settings: Settings) -> None:
    face = service(settings, FakeDetector([face_at()]), FakeEmbedder(OWNER, STRANGER), MemoryStore())
    with pytest.raises(DomainError) as error:
        face.enroll("w-1", frames(2), replace=True, actor="svc-people")
    assert error.value.status == 422


def test_enroll_frame_count(settings: Settings) -> None:
    face = service(settings, FakeDetector([face_at()]), FakeEmbedder(OWNER), MemoryStore())
    with pytest.raises(DomainError):
        face.enroll("w-1", frames(4), replace=True, actor="svc-people")


def test_check(settings: Settings) -> None:
    def check(faces: list) -> tuple[bool, QualityCode]:  # type: ignore[type-arg]
        result = service(settings, FakeDetector(faces), FakeEmbedder(OWNER), MemoryStore()).check(textured())
        return result.ok, result.code

    assert check([face_at()]) == (True, QualityCode.OK)
    assert check([]) == (False, QualityCode.NO_FACE)
    assert check([face_at(), face_at()]) == (False, QualityCode.MULTIPLE)
    assert check([face_at(size=40)]) == (False, QualityCode.TOO_SMALL)
