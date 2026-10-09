package ru.proxodnaya.access.features.qr.api;

/**
 * Подписи ECDSA P-256 ключами устройств (телефон, терминал): для фич, которым нужна та же криптография, что и QR.
 */
public interface SignatureCheck {

    /**
     * Приводит публичный ключ к хранимому виду.
     *
     * @param publicKey JWK, raw-точка или SPKI
     * @return SPKI base64url
     * @throws IllegalArgumentException если ключ не P-256
     */
    String normalize(String publicKey);

    /**
     * Проверяет подпись строки.
     *
     * @param publicKey ключ в виде из {@link #normalize(String)}
     * @param message   подписанная строка (UTF-8)
     * @param signature подпись base64url (P1363 или DER)
     * @return {@code true}, если подпись верна
     */
    boolean verify(String publicKey, String message, String signature);
}
