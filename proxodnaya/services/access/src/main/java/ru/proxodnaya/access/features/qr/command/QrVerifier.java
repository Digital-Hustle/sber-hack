package ru.proxodnaya.access.features.qr.command;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.features.qr.model.QrPayload;

/**
 * Проверяет подпись ECDSA-P256-SHA256 QR-кода по публичному ключу устройства
 * и окно времени ±90 с (3 окна по 30 с).
 *
 * <p>Публичный ключ хранится в формате X.509 SubjectPublicKeyInfo (DER, bytea в PostgreSQL).
 * Это стандартный формат генерации WebCrypto API в браузере (<code>crypto.subtle.exportKey("spki", key)</code>).</p>
 */
@Component
public class QrVerifier {

    private static final String EC_ALGORITHM = "EC";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /**
     * Проверяет подпись QR публичным ключом устройства.
     *
     * @param qr            распарсенный QR
     * @param publicKeyDer  публичный ключ устройства (X.509 SubjectPublicKeyInfo, DER)
     * @param signedMessage байты сообщения "v1|deviceId|window"
     * @return true если подпись валидна
     */
    public boolean verifySignature(QrPayload qr, byte[] publicKeyDer, byte[] signedMessage) {
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyDer);
            PublicKey publicKey = KeyFactory.getInstance(EC_ALGORITHM).generatePublic(keySpec);
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signedMessage);
            return verifier.verify(qr.signature());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Проверяет окно времени: QR валиден в пределах ±TOLERANCE_WINDOWS от текущего.
     *
     * @param qr  QR
     * @param now текущее время
     * @return true если окно в пределах допуска
     */
    public boolean isWindowValid(QrPayload qr, Instant now) {
        long currentWindow = now.getEpochSecond() / QrPayload.WINDOW_SECONDS;
        long diff = Math.abs(currentWindow - qr.window());
        return diff <= QrPayload.TOLERANCE_WINDOWS;
    }
}
