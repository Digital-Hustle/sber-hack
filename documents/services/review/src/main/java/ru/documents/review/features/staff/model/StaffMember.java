package ru.documents.review.features.staff.model;

import java.time.Instant;

/**
 * Сотрудник рабочего места.
 *
 * @param login     логин Keycloak (у новых — почта)
 * @param email     почта, на неё приходит код входа
 * @param firstName имя
 * @param lastName  фамилия
 * @param role      роль
 * @param enabled   вход разрешён
 * @param createdAt создан
 */
public record StaffMember(String login, String email, String firstName, String lastName, StaffRole role,
                          boolean enabled, Instant createdAt) {
}
