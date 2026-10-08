package ru.proxodnaya.access.features.qr.command.service;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Приводит публичный ключ устройства P-256 к X.509 SubjectPublicKeyInfo (DER).
 *
 * <p>Принимает: JWK ({@code {"kty":"EC","crv":"P-256","x":..,"y":..}}), base64url raw-точку
 * ({@code crypto.subtle.exportKey("raw")}, 65 байт, префикс 0x04) или base64/base64url SPKI.</p>
 */
@Component
public class PublicKeyCodec {

    private static final String EC = "EC";
    private static final int RAW_POINT_LENGTH = 65;
    private static final int COORDINATE_LENGTH = 32;
    private static final byte UNCOMPRESSED = 0x04;

    private final ObjectMapper objectMapper;

    public PublicKeyCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Нормализует ключ.
     *
     * @param publicKey ключ в одном из поддерживаемых форматов
     * @return SPKI DER
     * @throws IllegalArgumentException если ключ не P-256 или формат неизвестен
     */
    public byte[] toSpki(String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("Пустой публичный ключ устройства");
        }
        String value = publicKey.trim();
        try {
            if (value.startsWith("{")) {
                JsonNode jwk = objectMapper.readTree(value);
                if (!EC.equals(jwk.path("kty").asText()) || !"P-256".equals(jwk.path("crv").asText())) {
                    throw new IllegalArgumentException("Ожидается JWK EC P-256");
                }
                return fromPoint(decode(jwk.path("x").asText()), decode(jwk.path("y").asText()));
            }
            byte[] bytes = decode(value);
            if (bytes.length == RAW_POINT_LENGTH && bytes[0] == UNCOMPRESSED) {
                return fromPoint(Arrays.copyOfRange(bytes, 1, 1 + COORDINATE_LENGTH),
                        Arrays.copyOfRange(bytes, 1 + COORDINATE_LENGTH, RAW_POINT_LENGTH));
            }
            KeyFactory.getInstance(EC).generatePublic(new X509EncodedKeySpec(bytes));
            return bytes;
        } catch (JsonProcessingException | GeneralSecurityException error) {
            throw new IllegalArgumentException("Неверный публичный ключ устройства", error);
        }
    }

    private static byte[] decode(String value) {
        String normalized = value.replace('+', '-').replace('/', '_').replace("=", "");
        return Base64.getUrlDecoder().decode(normalized);
    }

    private static byte[] fromPoint(byte[] coordX, byte[] coordY) throws GeneralSecurityException {
        AlgorithmParameters params = AlgorithmParameters.getInstance(EC);
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(new BigInteger(1, coordX), new BigInteger(1, coordY));
        return KeyFactory.getInstance(EC).generatePublic(new ECPublicKeySpec(point, spec)).getEncoded();
    }
}
