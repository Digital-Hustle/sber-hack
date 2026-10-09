package ru.proxodnaya.access.features.qr.command.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.settings.api.PassTuning;
import ru.proxodnaya.access.features.qr.api.QrCheck;
import ru.proxodnaya.access.features.qr.api.QrCheckResult;
import ru.proxodnaya.access.features.qr.command.model.DeviceKey;
import ru.proxodnaya.access.features.qr.command.model.QrPayload;
import ru.proxodnaya.access.features.qr.command.repository.DeviceKeyRepository;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Проверка QR по порядку: формат → устройство известно → не отвязано → подпись → устройство владельца → окно.
 */
@Service
public class QrCheckService implements QrCheck {

    private final QrParser parser;
    private final EcdsaVerifier verifier;
    private final DeviceKeyRepository deviceKeys;
    private final PassTuning tuning;

    public QrCheckService(QrParser parser, EcdsaVerifier verifier, DeviceKeyRepository deviceKeys, PassTuning tuning) {
        this.parser = parser;
        this.verifier = verifier;
        this.deviceKeys = deviceKeys;
        this.tuning = tuning;
    }

    @Override
    @Transactional(readOnly = true)
    public QrCheckResult check(String raw, Instant now) {
        Optional<QrPayload> parsed = parser.parse(raw);
        if (parsed.isEmpty()) {
            ReasonCode code = raw == null || raw.isBlank() ? ReasonCode.QR_MISSING : ReasonCode.QR_INVALID;
            return QrCheckResult.fail(code, null, null, 0);
        }
        QrPayload payload = parsed.get();
        Optional<DeviceKey> key = deviceKeys.find(payload.deviceId());
        ReasonCode code = ReasonCode.OK;
        if (key.isEmpty()) {
            code = ReasonCode.DEVICE_UNKNOWN;
        } else if (key.get().revoked()) {
            code = ReasonCode.DEVICE_REVOKED;
        } else if (!verifier.verify(key.get().publicKey(), payload.signedMessage(), payload.signature())) {
            code = ReasonCode.QR_INVALID;
        } else if (!key.get().workerId().equals(payload.workerId())) {
            code = ReasonCode.DEVICE_MISMATCH;
        } else if (!isWindowValid(payload.window(), now)) {
            code = ReasonCode.QR_EXPIRED;
        }
        return code == ReasonCode.OK
                ? QrCheckResult.ok(payload.workerId(), payload.deviceId(), payload.window())
                : QrCheckResult.fail(code, payload.workerId(), payload.deviceId(), payload.window());
    }

    private boolean isWindowValid(long window, Instant now) {
        long current = Math.floorDiv(now.getEpochSecond(), QrPayload.WINDOW_SECONDS);
        long tolerance = tuning.qrTolerance().toSeconds() / QrPayload.WINDOW_SECONDS;
        return Math.abs(current - window) <= tolerance;
    }
}
