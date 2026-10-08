package ru.proxodnaya.access.features.qr.command;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.features.qr.model.QrPayload;
import ru.proxodnaya.access.shared.ReasonCode;

/**
 * Парсит QR-код формата v1.deviceId.window.signature.
 * Возвращает QrPayload или null если формат неверный (→ QR_INVALID / QR_MISSING).
 */
@Component
public class QrParser {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    /**
     * @param raw сырая строка QR (как отсканировано камерой киоска)
     * @return QrPayload или null, если формат неверный
     */
    public QrPayload parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        if (!QrPayload.VERSION.equals(parts[0])) {
            return null;
        }
        String deviceId = parts[1];
        if (deviceId.isBlank()) {
            return null;
        }
        long window;
        try {
            window = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (window <= 0) {
            return null;
        }
        byte[] signature;
        try {
            signature = URL_DECODER.decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (signature.length == 0) {
            return null;
        }
        return new QrPayload(deviceId, window, signature);
    }

    /**
     * Сообщение, которое подписано: "v1|deviceId|window".
     */
    public byte[] signedMessage(QrPayload qr) {
        return (QrPayload.VERSION + "|" + qr.deviceId() + "|" + qr.window())
                .getBytes(StandardCharsets.UTF_8);
    }
}
