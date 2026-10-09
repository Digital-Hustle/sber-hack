package ru.documents.review.features.staff.model;

import java.time.Instant;

/**
 * Сотрудник рабочего места.
 *
 * @param login          логин
 * @param firstName      имя
 * @param lastName       фамилия
 * @param role           роль
 * @param enabled        вход разрешён
 * @param codeConfigured код (TOTP) выдан
 * @param createdAt      создан
 */
public record StaffMember(String login, String firstName, String lastName, StaffRole role, boolean enabled,
                          boolean codeConfigured, Instant createdAt) {
}
