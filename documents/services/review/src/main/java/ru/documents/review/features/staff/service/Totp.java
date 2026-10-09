package ru.documents.review.features.staff.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Секрет TOTP в формате Keycloak: строка, ключ HMAC — её байты UTF-8; приложению отдаём их в Base32 (RFC 4648).
 */
public final class Totp {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int LENGTH = 32;
    private static final int BITS = 5;
    private static final int BYTE = 8;
    private static final int MASK = 31;
    private static final int OCTET = 0xFF;
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
     * Base32 без выравнивания.
     *
     * @param value секрет Keycloak
     * @return то, что вводят в приложение
     */
    public static String base32(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int left = 0;
        for (byte b : bytes) {
            buffer = buffer << BYTE | b & OCTET;
            left += BYTE;
            while (left >= BITS) {
                out.append(BASE32.charAt((buffer >> (left - BITS)) & MASK));
                left -= BITS;
            }
        }
        if (left > 0) {
            out.append(BASE32.charAt((buffer << (BITS - left)) & MASK));
        }
        return out.toString();
    }

    /**
     * Ссылка для QR.
     *
     * @param issuer подпись
     * @param login  логин
     * @param value  секрет Keycloak
     * @return otpauth://totp/…
     */
    public static String uri(String issuer, String login, String value) {
        String label = enc(issuer) + ":" + enc(login);
        return "otpauth://totp/" + label + "?secret=" + base32(value) + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
