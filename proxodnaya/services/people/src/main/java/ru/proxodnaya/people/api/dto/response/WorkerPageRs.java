package ru.proxodnaya.people.api.dto.response;

import java.util.List;

/**
 * Страница рабочих.
 *
 * @param items строки
 * @param page  курсор и всего
 */
public record WorkerPageRs(List<WorkerRs> items, PageRs page) {
}
