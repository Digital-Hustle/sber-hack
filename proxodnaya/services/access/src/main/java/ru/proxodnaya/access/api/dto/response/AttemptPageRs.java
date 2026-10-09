package ru.proxodnaya.access.api.dto.response;

import java.util.List;

/**
 * Страница журнала проходов: {@code {items, page: {next, total}}}.
 *
 * @param items строки, новые сверху
 * @param page  курсор и количество
 */
public record AttemptPageRs(List<AttemptRs> items, PageRs page) {
}
