package ru.proxodnaya.people.features.staff.model;

/**
 * Сессия после входа.
 *
 * @param accessToken      JWT people (ES256)
 * @param refreshToken     одноразовый токен продления
 * @param expiresAt        access-токен истекает, Unix ms
 * @param sessionExpiresAt сессия истекает, Unix ms
 * @param user             сотрудник
 */
public record StaffSession(String accessToken, String refreshToken, long expiresAt, long sessionExpiresAt, StaffUser user) {
}
