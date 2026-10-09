package ru.proxodnaya.people.features.device.command.service;

import java.text.ParseException;
import java.util.Map;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Проверка публичного ключа телефона: только EC P-256, точка на кривой, без закрытой части.
 */
public final class DeviceKeys {

    private DeviceKeys() {
    }

    /**
     * Нормализует JWK: оставляет только kty/crv/x/y.
     *
     * @param jwk ключ от телефона
     * @return публичный JWK
     * @throws DomainException VALIDATION_ERROR, если это не публичный ключ P-256
     */
    public static ECKey normalize(Map<String, Object> jwk) {
        if (jwk == null || jwk.containsKey("d")) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Нужен публичный ключ устройства");
        }
        try {
            ECKey key = ECKey.parse(jwk);
            if (!Curve.P_256.equals(key.getCurve())) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Ключ устройства должен быть ECDSA P-256");
            }
            return new ECKey.Builder(key.getCurve(), key.getX(), key.getY()).build();
        } catch (ParseException | IllegalArgumentException error) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Ключ устройства не разобран: нужен JWK EC P-256");
        }
    }
}
