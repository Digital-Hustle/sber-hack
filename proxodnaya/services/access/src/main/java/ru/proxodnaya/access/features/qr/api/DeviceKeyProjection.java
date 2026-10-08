package ru.proxodnaya.access.features.qr.api;

import java.time.Instant;

/**
 * Проекция ключей устройств из {@code people.device.v1}.
 */
public interface DeviceKeyProjection {

    /**
     * Привязка устройства ({@code DeviceBound}).
     *
     * @param deviceId  устройство
     * @param workerId  владелец
     * @param publicKey публичный ключ P-256: base64url raw (65 байт), base64 SPKI или JWK (JSON)
     * @param moment        момент события
     */
    void bind(String deviceId, String workerId, String publicKey, Instant moment);

    /**
     * Отвязка устройства ({@code DeviceRevoked}).
     *
     * @param deviceId устройство
     * @param moment       момент события
     */
    void revoke(String deviceId, Instant moment);
}
