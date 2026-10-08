package ru.proxodnaya.people.api.dto.response;

/**
 * Страница коллекции.
 *
 * @param next  курсор следующей страницы, {@code null} — страниц больше нет
 * @param total сколько всего под фильтром
 */
public record PageRs(String next, long total) {
}
