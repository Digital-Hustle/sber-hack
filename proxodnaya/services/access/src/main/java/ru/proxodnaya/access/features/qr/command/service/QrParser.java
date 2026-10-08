package ru.proxodnaya.access.features.qr.command.service;

import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.features.qr.command.model.QrPayload;

/**
 * Разбирает QR {@code v1.<workerId>.<deviceId>.<window>.<sig>}. Ничего не проверяет криптографически.
 */
@Component
public class QrParser {

    private static final int PARTS = 5;
    private static final int WORKER = 1;
    private static final int DEVICE = 2;
    private static final int WINDOW_PART = 3;
    private static final int SIGNATURE = 4;
    private static final int MAX_LENGTH = 1024;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern WINDOW = Pattern.compile("\\d{1,12}");

    /**
     * Разбирает строку QR.
     *
     * @param raw строка, как её прочитал киоск
     * @return разобранный QR или пусто, если формат неверный
     */
    public Optional<QrPayload> parse(String raw) {
        String[] parts = raw == null || raw.length() > MAX_LENGTH ? new String[0] : raw.trim().split("\\.", -1);
        if (!isWellFormed(parts)) {
            return Optional.empty();
        }
        try {
            byte[] signature = Base64.getUrlDecoder().decode(parts[SIGNATURE]);
            return Optional.of(new QrPayload(parts[WORKER], parts[DEVICE], Long.parseLong(parts[WINDOW_PART]), signature));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    private static boolean isWellFormed(String[] parts) {
        return parts.length == PARTS
                && QrPayload.VERSION.equals(parts[0])
                && ID.matcher(parts[WORKER]).matches()
                && ID.matcher(parts[DEVICE]).matches()
                && WINDOW.matcher(parts[WINDOW_PART]).matches()
                && !parts[SIGNATURE].isEmpty();
    }
}
