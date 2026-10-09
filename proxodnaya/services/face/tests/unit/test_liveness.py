from face.core.config import Settings
from face.domain import liveness
from face.domain.model import Challenge
from tests.conftest import face_at, make_settings


def test_yaw_and_pitch_of_frontal_face() -> None:
    face = face_at()
    assert abs(liveness.yaw(face)) < 1e-6
    assert abs(liveness.pitch(face) - 0.5) < 1e-6


def test_turn_left_moves_nose_right_on_unmirrored_frame(settings: Settings) -> None:
    frames = [face_at(), face_at(nose_shift=0.08), face_at(nose_shift=0.2)]
    assert liveness.passed(Challenge.TURN_LEFT, frames, settings)
    assert not liveness.passed(Challenge.TURN_RIGHT, frames, settings)


def test_turn_right(settings: Settings) -> None:
    frames = [face_at(nose_shift=0.02), face_at(nose_shift=-0.25)]
    assert liveness.passed(Challenge.TURN_RIGHT, frames, settings)


def test_mirrored_kiosk_inverts_direction() -> None:
    mirrored = make_settings(FACE_MIRRORED=True)
    frames = [face_at(), face_at(nose_shift=0.2)]
    assert liveness.passed(Challenge.TURN_RIGHT, frames, mirrored)
    assert not liveness.passed(Challenge.TURN_LEFT, frames, mirrored)


def test_photo_without_motion_fails_every_challenge(settings: Settings) -> None:
    frames = [face_at(), face_at(x=104), face_at(x=98, size=150)]
    for challenge in Challenge:
        assert not liveness.passed(challenge, frames, settings), challenge


def test_turn_needs_frontal_frame(settings: Settings) -> None:
    # фото в профиль, приложенное к камере: поворот «есть», но фронтального кадра нет
    frames = [face_at(nose_shift=0.3), face_at(nose_shift=0.31)]
    assert not liveness.passed(Challenge.TURN_LEFT, frames, settings)


def test_nod(settings: Settings) -> None:
    frames = [face_at(nose_drop=0.45), face_at(nose_drop=0.62)]
    assert liveness.passed(Challenge.NOD, frames, settings)


def test_single_frame_and_blink_fail(settings: Settings) -> None:
    assert not liveness.passed(Challenge.TURN_LEFT, [face_at(nose_shift=0.3)], settings)
    assert not liveness.passed(Challenge.BLINK, [face_at(), face_at(nose_shift=0.3)], settings)
