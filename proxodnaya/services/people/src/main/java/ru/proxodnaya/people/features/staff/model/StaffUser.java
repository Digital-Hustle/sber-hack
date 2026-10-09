package ru.proxodnaya.people.features.staff.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Сотрудник кабинета.
 *
 * @param id       id (UUIDv7)
 * @param name     имя
 * @param email    почта (вход по коду)
 * @param role     роль
 * @param status   статус
 * @param lastSeen последняя активность или {@code null}
 */
public record StaffUser(UUID id, String name, String email, StaffRole role, StaffStatus status, Instant lastSeen) {
}
