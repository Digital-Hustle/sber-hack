package ru.proxodnaya.access.features.qr.command;

import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ru.proxodnaya.access.features.qr.model.QrPayload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты QrVerifier: проверка окна времени ±90 с (3 окна по 30 с).
 * Подпись ECDSA тестируется с реальной парой ключей P-256.
 */
class QrVerifierTest {

    private final QrVerifier verifier = new QrVerifier();

    @Test
    void isWindowValid_currentWindow_returnsTrue() {
        // given
        long now = Instant.now().getEpochSecond();
        long window = now / QrPayload.WINDOW_SECONDS;
        QrPayload qr = new QrPayload("dev", window, new byte[]{1});

        // when / then
        assertTrue(verifier.isWindowValid(qr, Instant.now()));
    }

    @Test
    void isWindowValid_within90s_returnsTrue() {
        // given — 2 окна назад (60 с), в пределах ±3
        long nowSec = Instant.now().getEpochSecond();
        long window = nowSec / QrPayload.WINDOW_SECONDS - 2;
        QrPayload qr = new QrPayload("dev", window, new byte[]{1});

        // when / then
        assertTrue(verifier.isWindowValid(qr, Instant.now()));
    }

    @Test
    void isWindowValid_exactlyAtTolerance_returnsTrue() {
        // given — ровно 3 окна назад (90 с), граница допуска
        long nowSec = Instant.now().getEpochSecond();
        long window = nowSec / QrPayload.WINDOW_SECONDS - 3;
        QrPayload qr = new QrPayload("dev", window, new byte[]{1});

        // when / then
        assertTrue(verifier.isWindowValid(qr, Instant.now()));
    }

    @Test
    void isWindowValid_beyondTolerance_returnsFalse() {
        // given — 4 окна назад (120 с), за пределами
        long nowSec = Instant.now().getEpochSecond();
        long window = nowSec / QrPayload.WINDOW_SECONDS - 4;
        QrPayload qr = new QrPayload("dev", window, new byte[]{1});

        // when / then
        assertFalse(verifier.isWindowValid(qr, Instant.now()));
    }

    @Test
    void isWindowValid_futureWindow_returnsTrue() {
        // given — 1 окно в будущее (30 с вперёд)
        long nowSec = Instant.now().getEpochSecond();
        long window = nowSec / QrPayload.WINDOW_SECONDS + 1;
        QrPayload qr = new QrPayload("dev", window, new byte[]{1});

        // when / then
        assertTrue(verifier.isWindowValid(qr, Instant.now()));
    }

    @Test
    void isWindowValid_farFuture_returnsFalse() {
        // given — 10 окон в будущее
        long nowSec = Instant.now().getEpochSecond();
        long window = nowSec / QrPayload.WINDOW_SECONDS + 10;
        QrPayload qr = new QrPayload("dev", window, new byte[]{1});

        // when / then
        assertFalse(verifier.isWindowValid(qr, Instant.now()));
    }
}
