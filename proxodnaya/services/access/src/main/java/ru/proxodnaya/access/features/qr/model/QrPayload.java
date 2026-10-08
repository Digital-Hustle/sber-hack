package ru.proxodnaya.access.features.qr.model;

/**
 * Распарсенный QR-код рабочего: v1.<deviceId>.<window>.<sig>.
 * Формат: "v1.deviceId.window.signature" — точки разделители, base64url для подписи.
 *
 * @param deviceId id устройства (привязанного к рабочему)
 * @param window   окно времени = floor(unixTime / 30)
 * @param signature base64url ECDSA-P256-SHA256("v1|deviceId|window")
 */
public record QrPayload(
        String deviceId,
        long window,
        byte[] signature
) {
    public static final String VERSION = "v1";
    public static final long WINDOW_SECONDS = 30;
    public static final long TOLERANCE_WINDOWS = 3; // ±90 с
}
