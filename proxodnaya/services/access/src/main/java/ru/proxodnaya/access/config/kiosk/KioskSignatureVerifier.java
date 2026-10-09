package ru.proxodnaya.access.config.kiosk;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ru.proxodnaya.access.config.properties.AccessProperties;

/**
 * Проверка подписи запросов киоска (FR-64).
 * Заголовки: X-Kiosk-Id, X-Ts, X-Nonce, X-Signature.
 * Подпись: ECDSA-P256-SHA256("kiosk|kioskId|ts|nonce").
 * Окно: ts в пределах 90 с от серверного времени (защита от replay).
 */
@Component
@RequiredArgsConstructor
public class KioskSignatureVerifier {

    private static final String EC_ALGORITHM = "EC";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final Duration MAX_SKEW = Duration.ofSeconds(90);

    private final AccessProperties props;

    /**
     * Проверяет подпись запроса киоска.
     *
     * @param kioskId     id киоска (из X-Kiosk-Id)
     * @param ts          метка времени (из X-Ts, epoch millis)
     * @param nonce       одноразовый nonce (из X-Nonce)
     * @param signature   base64url подпись (из X-Signature)
     * @param publicKeyDer публичный ключ киоска (X.509 DER)
     * @param now         серверное время
     * @return true если подпись валидна и ts в окне
     */
    public boolean verify(String kioskId, long ts, String nonce, String signature, byte[] publicKeyDer, Instant now) {
        if (kioskId == null || kioskId.isBlank() || nonce == null || nonce.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        if (Math.abs(now.toEpochMilli() - ts) > MAX_SKEW.toMillis()) {
            return false;
        }
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyDer);
            PublicKey publicKey = KeyFactory.getInstance(EC_ALGORITHM).generatePublic(keySpec);
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signedMessage(kioskId, ts, nonce));
            byte[] sigBytes = java.util.Base64.getUrlDecoder().decode(signature);
            return verifier.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Сообщение для подписи: "kiosk|kioskId|ts|nonce".
     */
    public static byte[] signedMessage(String kioskId, long ts, String nonce) {
        return ("kiosk|" + kioskId + "|" + ts + "|" + nonce).getBytes(StandardCharsets.UTF_8);
    }
}
