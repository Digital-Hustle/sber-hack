package ru.documents.review.features.staff.model;

import java.util.List;

/**
 * Роль сотрудника и её realm-роли в Keycloak (старший — ещё и оператор, как в docs/ROLES.md).
 */
public enum StaffRole {
    OPERATOR(List.of("OPERATOR"), "Оператор"),
    SENIOR_OPERATOR(List.of("OPERATOR", "SENIOR_OPERATOR"), "Старший оператор"),
    AUDITOR(List.of("AUDITOR"), "Аудитор");

    private final List<String> realmRoles;
    private final String title;

    StaffRole(List<String> realmRoles, String title) {
        this.realmRoles = realmRoles;
        this.title = title;
    }

    /** Название по-русски (для писем). */
    public String title() {
        return title;
    }

    /** Realm-роли Keycloak. */
    public List<String> realmRoles() {
        return realmRoles;
    }

    /**
     * Главная роль по набору realm-ролей.
     *
     * @param roles realm-роли пользователя
     * @return роль или {@code null}, если это не сотрудник
     */
    public static StaffRole of(List<String> roles) {
        if (roles.contains("SENIOR_OPERATOR")) {
            return SENIOR_OPERATOR;
        }
        if (roles.contains("OPERATOR")) {
            return OPERATOR;
        }
        return roles.contains("AUDITOR") ? AUDITOR : null;
    }
}
