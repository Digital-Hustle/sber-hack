package ru.proxodnaya.people.features.staff.model;

import java.util.List;

/**
 * Страница.
 *
 * @param items строки
 * @param next  курсор следующей страницы или {@code null}
 * @param total всего под фильтром
 * @param <T>   тип строки
 */
public record StaffPage<T>(List<T> items, String next, long total) {
}
