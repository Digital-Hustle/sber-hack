package ru.proxodnaya.people.api.dto.response;

import java.util.UUID;

/**
 * Сотрудник кабинета.
 *
 * @param id       id
 * @param name     имя
 * @param email    почта
 * @param role     ADMIN | SECURITY_OFFICER | MANAGER | INSTALLER | GUARD
 * @param status   ACTIVE | INVITED | DISABLED
 * @param lastSeen последняя активность, Unix ms, или {@code null}
 */
public record StaffUserRs(UUID id, String name, String email, String role, String status, Long lastSeen) {
}
