package ru.proxodnaya.people.features.staff.model;

/**
 * Фильтр списка персонала.
 *
 * @param query  поиск по имени или почте
 * @param role   роль или {@code null}
 * @param status статус или {@code null}
 */
public record StaffFilter(String query, StaffRole role, StaffStatus status) {
}
