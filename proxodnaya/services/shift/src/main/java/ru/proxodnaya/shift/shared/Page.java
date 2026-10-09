package ru.proxodnaya.shift.shared;

import java.util.List;

/**
 * Страница списка.
 *
 * @param items строки
 * @param next  курсор следующей страницы или {@code null}
 * @param total сколько всего по фильтру
 * @param <T>   тип строки
 */
public record Page<T>(List<T> items, String next, long total) {
}
