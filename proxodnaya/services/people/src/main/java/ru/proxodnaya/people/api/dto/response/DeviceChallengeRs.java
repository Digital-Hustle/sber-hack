package ru.proxodnaya.people.api.dto.response;

/**
 * Challenge для device-токена.
 *
 * @param challenge подписать вместе с id устройства
 * @param expiresAt действует до, Unix ms
 */
public record DeviceChallengeRs(String challenge, long expiresAt) {
}
