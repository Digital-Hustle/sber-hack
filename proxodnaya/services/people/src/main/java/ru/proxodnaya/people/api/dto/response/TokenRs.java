package ru.proxodnaya.people.api.dto.response;

/**
 * Выпущенный people токен доступа.
 *
 * @param accessToken JWT (ES256) — в {@code Authorization: Bearer}
 * @param tokenType   всегда {@code Bearer}
 * @param expiresAt   истекает, Unix ms
 */
public record TokenRs(String accessToken, String tokenType, long expiresAt) {
}
