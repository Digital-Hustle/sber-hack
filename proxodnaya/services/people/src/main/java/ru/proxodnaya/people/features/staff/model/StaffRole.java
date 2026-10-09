package ru.proxodnaya.people.features.staff.model;

/**
 * Роли персонала (ADR-041). Совпадают с ролями realm Keycloak — сервисы проверяют одинаково.
 */
public enum StaffRole {
    ADMIN, SECURITY_OFFICER, MANAGER, INSTALLER, GUARD
}
