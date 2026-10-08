package ru.proxodnaya.access.features.qr.command.model;

import java.nio.charset.StandardCharsets;

/**
 * Разобранный QR пропуска {@code v1.<workerId>.<deviceId>.<window>.<sig>} (docs/BACKEND-CONTRACT.md).
 *
 * @param workerId  владелец
 * @param deviceId  устройство
 * @param window    {@code floor(unix_s / 30)}
 * @param signature ECDSA-P256-SHA256 строки {@code v1|workerId|deviceId|window}: raw r||s (WebCrypto) или DER
 */
public record QrPayload(String workerId, String deviceId, long window, byte[] signature) {

    public static final String VERSION = "v1";
    public static final long WINDOW_SECONDS = 30;

    /**
     * Строка, которую подписал телефон.
     *
     * @return байты {@code v1|workerId|deviceId|window} в UTF-8
     */
    public byte[] signedMessage() {
        return String.join("|", VERSION, workerId, deviceId, Long.toString(window)).getBytes(StandardCharsets.UTF_8);
    }
}
