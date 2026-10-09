package ru.proxodnaya.shift.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Пагинация: курсор следующей страницы и сколько всего.
 *
 * @param next  курсор или {@code null}, если страница последняя
 * @param total всего по фильтру
 */
public record PageRs(@Schema(nullable = true) String next, long total) {
}
