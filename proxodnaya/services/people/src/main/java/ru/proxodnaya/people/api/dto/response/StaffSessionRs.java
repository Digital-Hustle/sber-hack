package ru.proxodnaya.people.api.dto.response;

/**
 * Сессия персонала.
 *
 * @param accessToken      JWT people (ES256) — {@code Authorization: Bearer}
 * @param refreshToken     одноразовый, для {@code /auth/staff/refresh}
 * @param tokenType        {@code Bearer}
 * @param expiresAt        access-токен истекает, Unix ms
 * @param sessionExpiresAt сессия (12 ч) истекает, Unix ms
 * @param user             сотрудник
 */
public record StaffSessionRs(String accessToken, String refreshToken, String tokenType, long expiresAt, long sessionExpiresAt,
                             StaffUserRs user) {
}
