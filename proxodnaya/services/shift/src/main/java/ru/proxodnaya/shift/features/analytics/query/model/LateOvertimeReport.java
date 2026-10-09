package ru.proxodnaya.shift.features.analytics.query.model;

import java.time.Instant;
import java.util.List;

/**
 * Опоздания и переработки за период (B-03, FR-24).
 *
 * @param from    начало
 * @param to      конец, не включая
 * @param items   по рабочим: больше опозданий — выше
 * @param details по сменам — только когда задан рабочий, иначе пусто
 * @param method  как посчитано
 */
public record LateOvertimeReport(Instant from, Instant to, List<WorkerLateOvertime> items, List<ShiftOutcome> details,
                                 String method) {
}
