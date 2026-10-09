package ru.documents.review.features.workplace.model;

import java.util.List;

/**
 * Страница очереди.
 *
 * @param items  строки
 * @param next   курсор следующей страницы или null
 * @param total  всего по фильтру
 */
public record QueuePage(List<QueueItem> items, String next, long total) {
}
