package ru.documents.review.api.dto.response;

import java.util.List;
import ru.documents.review.features.workplace.model.QueueItem;

/**
 * Очередь.
 *
 * @param items строки
 * @param page  курсор и всего
 */
public record QueueRs(List<QueueItem> items, PageRs page) {
}
