package ru.proxodnaya.people.features.device.model;

/**
 * Challenge для device-токена.
 *
 * @param challenge случайное значение (base64url, 32 байта)
 * @param expiresAt действует до, Unix ms
 */
public record DeviceChallenge(String challenge, long expiresAt) {
}
