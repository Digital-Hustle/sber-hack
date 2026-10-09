package ru.proxodnaya.shift.api.dto.response;

import java.util.List;

/**
 * Страница смен.
 *
 * @param items смены
 * @param page  пагинация
 */
public record ShiftPageRs(List<ShiftRs> items, PageRs page) {
}
