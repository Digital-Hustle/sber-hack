package ru.proxodnaya.people.features.worker.model;

import java.util.List;

/**
 * Страница рабочих.
 *
 * @param items  строки
 * @param next   курсор следующей страницы или {@code null}
 * @param total  сколько всего под фильтром
 */
public record WorkerPage(List<Worker> items, String next, long total) {
}
