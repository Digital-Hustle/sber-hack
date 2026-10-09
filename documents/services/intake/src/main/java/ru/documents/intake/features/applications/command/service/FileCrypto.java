package ru.documents.intake.features.applications.command.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import ru.documents.intake.config.IntakeProperties;

/**
 * AES-256-GCM для файлов в БД (FR-62): iv(12) || ciphertext+tag.
 */
@Component
public class FileCrypto {

    private static final int IV = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final String ALG = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom rnd = new SecureRandom();

    public FileCrypto(IntakeProperties props) {
        byte[] k = Base64.getDecoder().decode(props.filesKey().trim());
        if (k.length != KEY_BYTES) {
            throw new IllegalStateException("FILES_ENCRYPTION_KEY must be 32 bytes base64");
        }
        this.key = new SecretKeySpec(k, "AES");
    }

    /**
     * Зашифровать.
     *
     * @param plain открытые байты
     * @return iv || шифртекст
     */
    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV];
            rnd.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain);
            return ByteBuffer.allocate(IV + ct.length).put(iv).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    /**
     * Расшифровать (проверка целостности тегом GCM).
     *
     * @param enc iv || шифртекст
     * @return открытые байты
     */
    public byte[] decrypt(byte[] enc) {
        try {
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, enc, 0, IV));
            return c.doFinal(enc, IV, enc.length - IV);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }
}
