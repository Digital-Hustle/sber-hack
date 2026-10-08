package ru.proxodnaya.access.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ru.proxodnaya.access.features.qr.api.QrCheckResult;
import ru.proxodnaya.access.features.qr.command.model.DeviceKey;
import ru.proxodnaya.access.features.qr.command.repository.DeviceKeyRepository;
import ru.proxodnaya.access.features.qr.command.service.EcdsaVerifier;
import ru.proxodnaya.access.features.qr.command.service.QrCheckService;
import ru.proxodnaya.access.features.qr.command.service.QrParser;
import ru.proxodnaya.access.shared.ReasonCode;
import ru.proxodnaya.access.support.TestKeys;
import ru.proxodnaya.access.support.TestProps;

class QrCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T09:30:00Z");
    private static final long WINDOW = TestKeys.window(NOW);

    private final TestKeys keys = TestKeys.generate();
    private final DeviceKeyRepository repo = mock(DeviceKeyRepository.class);
    private final QrCheckService service = new QrCheckService(new QrParser(), new EcdsaVerifier(), repo, TestProps.defaults());

    @Test
    void check_validQr_ok() {
        // given
        bound("w1", false);

        // when
        QrCheckResult result = service.check(keys.qr("w1", "d1", WINDOW), NOW);

        // then
        assertThat(result.code()).isEqualTo(ReasonCode.OK);
        assertThat(result.workerId()).isEqualTo("w1");
        assertThat(result.window()).isEqualTo(WINDOW);
    }

    @Test
    void check_windowEdges_toleranceIs90Seconds() {
        // given
        bound("w1", false);

        // when / then
        assertThat(service.check(keys.qr("w1", "d1", WINDOW - 3), NOW).code()).isEqualTo(ReasonCode.OK);
        assertThat(service.check(keys.qr("w1", "d1", WINDOW + 3), NOW).code()).isEqualTo(ReasonCode.OK);
        assertThat(service.check(keys.qr("w1", "d1", WINDOW - 4), NOW).code()).isEqualTo(ReasonCode.QR_EXPIRED);
    }

    @Test
    void check_failures_mappedToCodes() {
        // given
        when(repo.find("unknown")).thenReturn(Optional.empty());
        bound("w1", false);

        // when / then
        assertThat(service.check(null, NOW).code()).isEqualTo(ReasonCode.QR_MISSING);
        assertThat(service.check("garbage", NOW).code()).isEqualTo(ReasonCode.QR_INVALID);
        assertThat(service.check(keys.qr("w1", "unknown", WINDOW), NOW).code()).isEqualTo(ReasonCode.DEVICE_UNKNOWN);
        assertThat(service.check(TestKeys.generate().qr("w1", "d1", WINDOW), NOW).code()).isEqualTo(ReasonCode.QR_INVALID);
        assertThat(service.check(keys.qr("w2", "d1", WINDOW), NOW).code()).isEqualTo(ReasonCode.DEVICE_MISMATCH);
    }

    @Test
    void check_revokedDevice_deviceRevoked() {
        // given
        bound("w1", true);

        // when / then
        assertThat(service.check(keys.qr("w1", "d1", WINDOW), NOW).code()).isEqualTo(ReasonCode.DEVICE_REVOKED);
    }

    private void bound(String workerId, boolean revoked) {
        when(repo.find("d1")).thenReturn(Optional.of(new DeviceKey("d1", workerId, keys.spki(), revoked)));
    }
}
