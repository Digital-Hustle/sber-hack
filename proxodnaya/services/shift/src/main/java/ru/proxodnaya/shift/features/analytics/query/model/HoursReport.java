package ru.proxodnaya.shift.features.analytics.query.model;

import java.time.Instant;
import java.util.List;

/**
 * Отработанные часы за период (FR-24/33).
 *
 * @param from   начало
 * @param to     конец, не включая
 * @param items  по рабочим, больше часов — выше
 * @param method как посчитано
 */
public record HoursReport(Instant from, Instant to, List<WorkerHours> items, String method) {
}
