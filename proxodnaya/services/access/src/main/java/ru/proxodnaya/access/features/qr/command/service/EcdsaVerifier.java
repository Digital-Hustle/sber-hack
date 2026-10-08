package ru.proxodnaya.access.features.qr.command.service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

import org.springframework.stereotype.Component;

/**
 * Проверка ECDSA P-256 / SHA-256.
 *
 * <p>WebCrypto ({@code crypto.subtle.sign}) отдаёт подпись в формате IEEE P1363 (r||s, 64 байта),
 * а JCA {@code SHA256withECDSA} ждёт DER. Поддерживаем оба: 64 байта — P1363, иначе DER.</p>
 */
@Component
public class EcdsaVerifier {

    private static final int P1363_P256_LENGTH = 64;

    /**
     * Проверяет подпись.
     *
     * @param spkiDer   публичный ключ, X.509 SubjectPublicKeyInfo
     * @param message   подписанные байты
     * @param signature подпись (P1363 или DER)
     * @return {@code true}, если подпись верна
     */
    public boolean verify(byte[] spkiDer, byte[] message, byte[] signature) {
        try {
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spkiDer));
            String algorithm = signature.length == P1363_P256_LENGTH ? "SHA256withECDSAinP1363Format" : "SHA256withECDSA";
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            return false;
        }
    }
}
