package ru.documents.review.api.dto.response;

/**
 * Страница коллекции.
 *
 * @param next  курсор следующей страницы или null
 * @param total всего
 */
public record PageRs(String next, long total) {
}
