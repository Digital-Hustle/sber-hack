from collections.abc import Callable

import numpy as np
import pytest
from fastapi.testclient import TestClient

from face.core.config import Settings
from face.core.security import TokenVerifier
from face.domain.model import StoredTemplate
from face.domain.service import FaceService
from face.infra.images import encode
from face.main import create_app
from tests.conftest import FakeDetector, FakeEmbedder, MemoryStore, face_at, textured, unit

Issue = Callable[..., str]
FRAME = encode(textured())


@pytest.fixture
def store() -> MemoryStore:
    return MemoryStore()


@pytest.fixture
def client(settings: Settings, verifier: TokenVerifier, store: MemoryStore) -> TestClient:
    detector = FakeDetector([face_at()], [face_at(nose_shift=0.25)])
    service = FaceService(detector, FakeEmbedder(unit(1, 0)), store, settings)
    return TestClient(create_app(service, verifier, settings, ready=lambda: True))


def auth(token: Issue, *roles: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token(list(roles))}"}


def test_verify_contract(client: TestClient, token: Issue, store: MemoryStore) -> None:
    store.data["w-1"] = [StoredTemplate(unit(1, 0), "fake-1")]
    response = client.post(
        "/internal/face/verify",
        json={"workerId": "w-1", "frames": [FRAME, f"data:image/jpeg;base64,{FRAME}"], "challenge": "TURN_LEFT"},
        headers=auth(token, "SVC_ACCESS"),
    )
    assert response.status_code == 200
    assert response.json() == {"match": True, "score": 1.0, "liveness": True, "quality": pytest.approx(1.0, abs=0.5)}


def test_verify_not_enrolled_has_reason(client: TestClient, token: Issue) -> None:
    response = client.post(
        "/internal/face/verify",
        json={"workerId": "w-9", "frames": [FRAME], "challenge": "NOD"},
        headers=auth(token, "SVC_ACCESS"),
    )
    assert response.json()["reason"] == "FACE_NOT_ENROLLED"
    assert response.json()["match"] is False


def test_auth(client: TestClient, token: Issue) -> None:
    body = {"workerId": "w-1", "frames": [FRAME], "challenge": "NOD"}
    assert client.post("/internal/face/verify", json=body).status_code == 401
    assert client.post("/internal/face/verify", json=body, headers={"Authorization": "Bearer x"}).status_code == 401
    wrong_aud = {"Authorization": f"Bearer {token(['SVC_ACCESS'], audience='people')}"}
    assert client.post("/internal/face/verify", json=body, headers=wrong_aud).status_code == 401
    expired = {"Authorization": f"Bearer {token(['SVC_ACCESS'], ttl=-60)}"}
    assert client.post("/internal/face/verify", json=body, headers=expired).status_code == 401
    forbidden = client.post("/internal/face/verify", json=body, headers=auth(token, "SVC_PEOPLE"))
    assert forbidden.status_code == 403
    assert forbidden.json()["code"] == "FORBIDDEN"
    assert forbidden.headers["content-type"] == "application/problem+json"


def test_validation_errors(client: TestClient, token: Issue) -> None:
    headers = auth(token, "SVC_ACCESS")
    response = client.post(
        "/internal/face/verify", json={"workerId": "w-1", "frames": [], "challenge": "JUMP"}, headers=headers
    )
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"
    assert {error["field"] for error in response.json()["errors"]} == {"frames", "challenge"}
    broken = client.post(
        "/internal/face/verify", json={"workerId": "w-1", "frames": ["@@@"], "challenge": "NOD"}, headers=headers
    )
    assert broken.status_code == 400
    assert broken.json()["code"] == "FRAME_INVALID"
    not_image = client.post(
        "/internal/face/verify", json={"workerId": "w-1", "frames": ["aGVsbG8="], "challenge": "NOD"}, headers=headers
    )
    assert not_image.json()["code"] == "FRAME_INVALID"


def test_frame_too_large(settings: Settings, verifier: TokenVerifier, token: Issue) -> None:
    small = settings.model_copy(update={"max_frame_bytes": 100})
    service = FaceService(FakeDetector([face_at()]), FakeEmbedder(unit(1, 0)), MemoryStore(), small)
    client = TestClient(create_app(service, verifier, small))
    response = client.post(
        "/internal/face/verify",
        json={"workerId": "w-1", "frames": [FRAME], "challenge": "NOD"},
        headers=auth(token, "SVC_ACCESS"),
    )
    assert response.status_code == 413


def test_enroll_check_delete(client: TestClient, token: Issue, store: MemoryStore) -> None:
    headers = auth(token, "SVC_PEOPLE")
    enrolled = client.post("/internal/face/enroll", json={"workerId": "w-1", "frames": [FRAME]}, headers=headers)
    assert enrolled.status_code == 200
    assert enrolled.json()["templates"] == 1
    assert len(store.data["w-1"]) == 1
    checked = client.post("/internal/face/check", json={"photo": FRAME}, headers=headers)
    assert checked.json()["ok"] is True
    assert checked.json()["code"] == "OK"
    assert client.delete("/internal/face/workers/w-1", headers=headers).status_code == 204
    assert "w-1" not in store.data
    assert client.delete("/internal/face/workers/w-1", headers=auth(token, "SVC_ACCESS")).status_code == 403


def test_enroll_bad_photo_is_422(settings: Settings, verifier: TokenVerifier, token: Issue) -> None:
    service = FaceService(FakeDetector([]), FakeEmbedder(unit(1, 0)), MemoryStore(), settings)
    client = TestClient(create_app(service, verifier, settings))
    response = client.post(
        "/internal/face/enroll", json={"workerId": "w-1", "frames": [FRAME]}, headers=auth(token, "SVC_PEOPLE")
    )
    assert response.status_code == 422
    assert response.json()["code"] == "FACE_LOW_QUALITY"
    assert response.json()["errors"][0]["code"] == "NO_FACE"


def test_health_and_metrics(client: TestClient, settings: Settings, verifier: TokenVerifier) -> None:
    assert client.get("/health/live").json() == {"status": "UP"}
    assert client.get("/health/ready").status_code == 200
    down = TestClient(
        create_app(
            FaceService(FakeDetector([]), FakeEmbedder(), MemoryStore(), settings),
            verifier,
            settings,
            ready=lambda: False,
        )
    )
    assert down.get("/health/ready").status_code == 503
    assert "face_verify_seconds" in client.get("/metrics").text


def test_unexpected_error_is_problem(settings: Settings, verifier: TokenVerifier, token: Issue) -> None:
    class Broken(MemoryStore):
        def load(self, worker_id: str) -> list[StoredTemplate]:
            raise RuntimeError("db down")

    service = FaceService(FakeDetector([face_at()]), FakeEmbedder(np.ones(2, np.float32)), Broken(), settings)
    client = TestClient(create_app(service, verifier, settings), raise_server_exceptions=False)
    response = client.post(
        "/internal/face/verify",
        json={"workerId": "w-1", "frames": [FRAME], "challenge": "NOD"},
        headers=auth(token, "SVC_ACCESS"),
    )
    assert response.status_code == 500
    assert response.json()["code"] == "SYSTEM_ERROR"
    assert "db down" not in response.text
