package ru.proxodnaya.shift.features.report.query.model;

import java.time.Instant;

/**
 * Плановая смена для кода «Н».
 *
 * @param workerId рабочий
 * @param start    начало
 */
public record PlannedShift(String workerId, Instant start) {
}
