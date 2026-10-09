package ru.proxodnaya.people.features.device.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Привязанный телефон рабочего.
 *
 * @param deviceId     id устройства (генерирует телефон)
 * @param workerId     рабочий
 * @param publicKeyJwk публичный ключ ECDSA P-256 (JWK, только kty/crv/x/y)
 * @param status       ACTIVE | REVOKED
 * @param boundAt      привязан
 * @param revokedAt    отвязан или {@code null}
 */
public record Device(String deviceId, UUID workerId, String publicKeyJwk, String status, Instant boundAt,
                     Instant revokedAt) {

    /** Действующее устройство. */
    public static final String ACTIVE = "ACTIVE";
    /** Отвязанное устройство. */
    public static final String REVOKED = "REVOKED";
}
