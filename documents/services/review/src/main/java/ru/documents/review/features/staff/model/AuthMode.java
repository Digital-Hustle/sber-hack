package ru.documents.review.features.staff.model;

import java.util.List;

/**
 * Режим стенда для формы входа: {@code dev} — тестовый (быстрый вход, код 000000), {@code prod} — боевой.
 *
 * @param mode      dev или prod
 * @param testUsers сотрудники для быстрого входа (пусто в prod)
 */
public record AuthMode(String mode, List<TestUser> testUsers) {

    /**
     * Сотрудник для быстрого входа.
     *
     * @param email почта
     * @param name  имя и фамилия
     * @param role  роль
     */
    public record TestUser(String email, String name, StaffRole role) {
    }
}
