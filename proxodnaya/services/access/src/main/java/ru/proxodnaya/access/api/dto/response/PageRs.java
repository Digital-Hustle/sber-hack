package ru.proxodnaya.access.api.dto.response;

/**
 * Страница коллекции (BACKEND-CONTRACT.md «Общие правила»).
 *
 * @param next  курсор следующей страницы или {@code null}
 * @param total сколько всего по фильтру
 */
public record PageRs(String next, long total) {
}
