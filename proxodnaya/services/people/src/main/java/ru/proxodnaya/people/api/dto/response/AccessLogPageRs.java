package ru.proxodnaya.people.api.dto.response;

import java.util.List;

/**
 * Страница журнала доступа.
 *
 * @param items записи
 * @param page  курсор и total
 */
public record AccessLogPageRs(List<AccessEntryRs> items, PageRs page) {
}
