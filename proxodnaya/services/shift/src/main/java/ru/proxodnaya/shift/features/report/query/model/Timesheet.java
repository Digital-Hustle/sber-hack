package ru.proxodnaya.shift.features.report.query.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Табель за период (B-06).
 *
 * @param from        первый день
 * @param to          последний день, включительно
 * @param contractor  фильтр по подрядчику или {@code null}
 * @param zone        часовой пояс площадки, в котором считаются дни
 * @param days        дни периода
 * @param rows        строки
 * @param generatedAt когда сформирован
 * @param method      как посчитано
 */
public record Timesheet(LocalDate from, LocalDate to, String contractor, ZoneId zone, List<LocalDate> days,
                        List<TimesheetRow> rows, Instant generatedAt, String method) {
}
