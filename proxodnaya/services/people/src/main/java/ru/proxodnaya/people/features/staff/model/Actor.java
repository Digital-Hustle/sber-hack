package ru.proxodnaya.people.features.staff.model;

import java.util.UUID;

/**
 * Кто выполняет действие (из токена).
 *
 * @param id   сотрудник или {@code null}, если {@code sub} не UUID
 * @param name имя для журнала
 */
public record Actor(UUID id, String name) {

    /**
     * Сам сотрудник.
     *
     * @param user сотрудник
     * @return актор
     */
    public static Actor of(StaffUser user) {
        return new Actor(user.id(), user.name());
    }
}
