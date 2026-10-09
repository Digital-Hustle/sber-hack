package ru.proxodnaya.people.api.dto.response;

import java.util.List;

/**
 * Страница персонала.
 *
 * @param items сотрудники
 * @param page  курсор и total
 */
public record StaffPageRs(List<StaffUserRs> items, PageRs page) {
}
