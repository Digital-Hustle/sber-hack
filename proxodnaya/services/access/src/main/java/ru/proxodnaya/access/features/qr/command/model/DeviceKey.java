package ru.proxodnaya.access.features.qr.command.model;

/**
 * Ключ устройства из проекции {@code device_keys}.
 *
 * @param deviceId  устройство
 * @param workerId  владелец
 * @param publicKey X.509 SubjectPublicKeyInfo (DER)
 * @param revoked   устройство отвязано
 */
public record DeviceKey(String deviceId, String workerId, byte[] publicKey, boolean revoked) {
}
