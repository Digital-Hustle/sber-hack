package ru.proxodnaya.people.features.device.command.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import ru.proxodnaya.people.config.properties.PeopleProperties;

/**
 * Коды инвайтов: 6 символов без похожих (0/O, 1/I), хранение — HMAC-SHA256 с секретом.
 */
@Component
public class InviteCodes {

    /** Алфавит как в PWA: без 0, 1, I, O. */
    static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    /** Длина кода. */
    static final int LENGTH = 6;
    private static final String HMAC = "HmacSHA256";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public InviteCodes(PeopleProperties properties) {
        this.key = new SecretKeySpec(properties.invite().pepper().getBytes(StandardCharsets.UTF_8), HMAC);
    }

    /**
     * Новый случайный код.
     *
     * @return код
     */
    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int idx = 0; idx < LENGTH; idx++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Хэш кода для поиска в БД (регистр и пробелы не важны).
     *
     * @param code код от человека
     * @return hex HMAC
     * @throws IllegalStateException если в JVM нет HmacSHA256
     */
    public String hash(String code) {
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC недоступен", error);
        }
    }
}
