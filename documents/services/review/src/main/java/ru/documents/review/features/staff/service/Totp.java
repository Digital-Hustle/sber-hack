package ru.documents.review.features.staff.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Одноразовый код = TOTP (RFC 6238, HMAC-SHA1, 6 цифр) в формате Keycloak: секрет — строка, ключ HMAC — её байты.
 * Шаг — срок жизни кода из письма; проверяет код сам Keycloak.
 */
public final class Totp {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 32;
    private static final int DIGITS = 1_000_000;
    private static final int LAST = 19;
    private static final int NIBBLE = 0xF;
    private static final int SIGN = 0x7FFFFFFF;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** Новый секрет (32 символа ≈ 190 бит). */
    public static String newSecret() {
        StringBuilder s = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            s.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return s.toString();
    }

    /**
     * Код на момент времени.
     *
     * @param secret      секрет Keycloak
     * @param epochSecond время, секунды Unix
     * @param period      шаг, секунды
     * @return 6 цифр
     */
    public static String code(String secret, long epochSecond, long period) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(epochSecond / period).array());
            int o = h[LAST] & NIBBLE;
            int v = ByteBuffer.wrap(h, o, Integer.BYTES).getInt() & SIGN;
            return String.format("%06d", v % DIGITS);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 недоступен", e);
        }
    }
}
