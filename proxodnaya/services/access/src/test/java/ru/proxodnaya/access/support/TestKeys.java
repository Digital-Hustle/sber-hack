package ru.proxodnaya.access.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Ключ телефона и QR так, как их делает WebCrypto: P-256, подпись P1363 (r||s), ключ — raw-точка.
 */
public final class TestKeys {

    private static final int COORD = 32;

    private final KeyPair pair;

    private TestKeys(KeyPair pair) {
        this.pair = pair;
    }

    /**
     * Новый ключ устройства.
     *
     * @return ключ
     */
    public static TestKeys generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return new TestKeys(generator.generateKeyPair());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    /**
     * Окно QR для момента.
     *
     * @param moment момент
     * @return {@code floor(unix_s / 30)}
     */
    public static long window(Instant moment) {
        return Math.floorDiv(moment.getEpochSecond(), 30);
    }

    /**
     * SPKI DER.
     *
     * @return ключ
     */
    public byte[] spki() {
        return pair.getPublic().getEncoded();
    }

    /**
     * Raw-точка base64url, как {@code crypto.subtle.exportKey("raw")}.
     *
     * @return ключ
     */
    public String rawB64u() {
        ECPublicKey pub = (ECPublicKey) pair.getPublic();
        byte[] out = new byte[1 + 2 * COORD];
        out[0] = 0x04;
        System.arraycopy(fixed(pub.getW().getAffineX().toByteArray()), 0, out, 1, COORD);
        System.arraycopy(fixed(pub.getW().getAffineY().toByteArray()), 0, out, 1 + COORD, COORD);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
    }

    /**
     * JWK.
     *
     * @return JSON
     */
    public String jwk() {
        ECPublicKey pub = (ECPublicKey) pair.getPublic();
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"" + enc.encodeToString(fixed(pub.getW().getAffineX().toByteArray()))
                + "\",\"y\":\"" + enc.encodeToString(fixed(pub.getW().getAffineY().toByteArray())) + "\"}";
    }

    /**
     * Подпись P1363.
     *
     * @param message строка
     * @return подпись
     */
    public byte[] signP1363(String message) {
        return sign("SHA256withECDSAinP1363Format", message);
    }

    /**
     * Подпись DER.
     *
     * @param message строка
     * @return подпись
     */
    public byte[] signDer(String message) {
        return sign("SHA256withECDSA", message);
    }

    /**
     * QR пропуска.
     *
     * @param workerId рабочий
     * @param deviceId устройство
     * @param window   окно
     * @return строка QR
     */
    public String qr(String workerId, String deviceId, long window) {
        String sig = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signP1363("v1|" + workerId + "|" + deviceId + "|" + window));
        return "v1." + workerId + "." + deviceId + "." + window + "." + sig;
    }

    private byte[] sign(String algorithm, String message) {
        try {
            Signature signer = Signature.getInstance(algorithm);
            signer.initSign(pair.getPrivate());
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return signer.sign();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private static byte[] fixed(byte[] value) {
        byte[] out = new byte[COORD];
        byte[] src = value.length > COORD ? Arrays.copyOfRange(value, value.length - COORD, value.length) : value;
        System.arraycopy(src, 0, out, COORD - src.length, src.length);
        return out;
    }
}
