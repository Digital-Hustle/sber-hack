package ru.proxodnaya.shift.api.dto.response;

import java.util.List;

/**
 * Страница интервалов.
 *
 * @param items интервалы, новые сверху
 * @param page  пагинация
 */
public record IntervalPageRs(List<IntervalRs> items, PageRs page) {
}
