package ru.proxodnaya.access.features.qr.command;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.access.features.qr.model.QrPayload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Тесты QrParser: парсинг формата v1.deviceId.window.signature.
 */
class QrParserTest {

    private final QrParser parser = new QrParser();

    @Test
    void parse_validQr_returnsPayload() {
        // given
        String raw = "v1.dev_001.1234567.AAAAAAAAAAA";

        // when
        QrPayload result = parser.parse(raw);

        // then
        assertNotNull(result);
        assertEquals("dev_001", result.deviceId());
        assertEquals(1234567L, result.window());
        assertNotNull(result.signature());
        assertEquals(8, result.signature().length);
    }

    @Test
    void parse_nullInput_returnsNull() {
        // given / when
        QrPayload result = parser.parse(null);

        // then
        assertNull(result);
    }

    @Test
    void parse_blankInput_returnsNull() {
        // given / when
        QrPayload result = parser.parse("  ");

        // then
        assertNull(result);
    }

    @Test
    void parse_wrongVersion_returnsNull() {
        // given
        String raw = "v2.dev_001.1234567.AAAAAAAAAAA";

        // when
        QrPayload result = parser.parse(raw);

        // then
        assertNull(result);
    }

    @Test
    void parse_notEnoughParts_returnsNull() {
        // given
        String raw = "v1.dev_001.1234567";

        // when
        QrPayload result = parser.parse(raw);

        // then
        assertNull(result);
    }

    @Test
    void parse_windowNotNumber_returnsNull() {
        // given
        String raw = "v1.dev_001.ABC.AAAAAAAAAAA";

        // when
        QrPayload result = parser.parse(raw);

        // then
        assertNull(result);
    }

    @Test
    void parse_windowZero_returnsNull() {
        // given
        String raw = "v1.dev_001.0.AAAAAAAAAAA";

        // when
        QrPayload result = parser.parse(raw);

        // then
        assertNull(result);
    }

    @Test
    void parse_emptyDeviceId_returnsNull() {
        // given
        String raw = "v1..1234567.AAAAAAAAAAA";

        // when
        QrPayload result = parser.parse(raw);

        // then
        assertNull(result);
    }

    @Test
    void signedMessage_correctFormat() {
        // given
        QrPayload qr = new QrPayload("dev_001", 1234567L, new byte[]{1, 2, 3});

        // when
        byte[] msg = parser.signedMessage(qr);

        // then
        assertEquals("v1|dev_001|1234567", new String(msg));
    }
}
