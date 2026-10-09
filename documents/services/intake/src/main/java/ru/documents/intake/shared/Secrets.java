package ru.documents.intake.shared;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Случайные ключи заявки/коды и их хэши (HMAC-SHA256 с перцем). В БД — только хэши.
 */
public final class Secrets {

    private static final SecureRandom RND = new SecureRandom();
    private static final int KEY_BYTES = 32;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LEN = 8;

    private Secrets() {
    }

    /**
     * Ключ заявки 256 бит.
     *
     * @return base64url
     */
    public static String accessKey() {
        byte[] b = new byte[KEY_BYTES];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /**
     * Короткий код возврата (8 символов без похожих букв).
     *
     * @return код
     */
    public static String resumeCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CODE_ALPHABET.charAt(RND.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * HMAC-SHA256.
     *
     * @param pepper перец
     * @param value  значение
     * @return hex
     */
    public static String hash(String pepper, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
