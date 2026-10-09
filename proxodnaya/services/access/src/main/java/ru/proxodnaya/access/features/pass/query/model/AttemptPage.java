package ru.proxodnaya.access.features.pass.query.model;

import java.util.List;

/**
 * Страница журнала.
 *
 * @param items строки, новые сверху
 * @param next  курсор следующей страницы или {@code null}, если это последняя
 * @param total сколько всего строк по фильтру
 */
public record AttemptPage(List<AttemptView> items, String next, long total) {
}
