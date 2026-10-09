import base64

import numpy as np
import pytest
from cryptography.exceptions import InvalidTag

from face.domain import matcher
from face.infra.crypto import TemplateCipher
from tests.conftest import KEY, unit


def test_cosine_and_pairwise() -> None:
    first, second, third = unit(1, 0), unit(1, 1), unit(0, 1)
    assert matcher.cosine(first, first) == pytest.approx(1.0)
    assert matcher.cosine(first, third) == pytest.approx(0.0)
    assert matcher.cosine(first, np.zeros(2, dtype=np.float32)) == 0.0
    assert matcher.min_pairwise([first]) == 1.0
    assert matcher.min_pairwise([first, second, third]) == pytest.approx(0.0)
    assert matcher.best([first], [third, second]) == pytest.approx(0.7071, abs=1e-3)
    assert matcher.best([first], []) == 0.0


def test_encrypt_round_trip_bound_to_worker() -> None:
    cipher = TemplateCipher(KEY)
    vector = unit(*range(1, 129))
    ciphertext, nonce = cipher.encrypt("w-1", vector)
    assert vector.tobytes() not in ciphertext
    assert np.allclose(cipher.decrypt("w-1", ciphertext, nonce), vector)
    with pytest.raises(InvalidTag):
        cipher.decrypt("w-2", ciphertext, nonce)


def test_key_must_be_256_bit() -> None:
    with pytest.raises(ValueError, match="32"):
        TemplateCipher(base64.b64encode(b"short").decode())
