package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ru.proxodnaya.access.features.qr.command.model.QrPayload;
import ru.proxodnaya.access.features.qr.command.service.QrParser;

class QrParserTest {

    private final QrParser parser = new QrParser();

    @Test
    void parse_contractFormat_returnsAllParts() {
        // given
        String raw = "v1.w_42.dev-7.59000000.AAEC";

        // when
        QrPayload payload = parser.parse(raw).orElseThrow();

        // then
        assertThat(payload.workerId()).isEqualTo("w_42");
        assertThat(payload.deviceId()).isEqualTo("dev-7");
        assertThat(payload.window()).isEqualTo(59_000_000L);
        assertThat(payload.signature()).containsExactly(0, 1, 2);
        assertThat(new String(payload.signedMessage())).isEqualTo("v1|w_42|dev-7|59000000");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "v1.dev.59000000.AAEC",
        "v2.w.dev.59000000.AAEC",
        "v1.w.dev.-5.AAEC",
        "v1.w.dev.abc.AAEC",
        "v1.w.dev.59000000.",
        "v1.w.dev.59000000.!!!",
        "v1.w|x.dev.59000000.AAEC",
        "v1..dev.59000000.AAEC",
        "v1.w.dev.59000000.AAEC.extra"
    })
    void parse_malformed_returnsEmpty(String raw) {
        // when / then
        assertThat(parser.parse(raw)).isEmpty();
    }

    @Test
    void parse_tooLong_returnsEmpty() {
        // given
        String raw = "v1.w.dev.59000000." + "A".repeat(2000);

        // when / then
        assertThat(parser.parse(raw)).isEmpty();
    }
}
