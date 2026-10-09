package ru.proxodnaya.shift.features.interval.model;

import ru.proxodnaya.shift.shared.Period;

/**
 * Фильтр интервалов: период по входу, рабочий, зона.
 *
 * @param period            период по входу
 * @param workerId          рабочий или {@code null}
 * @param zoneId            зона или {@code null}
 * @param includeSimulated  включать интервалы симулятора
 */
public record IntervalFilter(Period period, String workerId, String zoneId, boolean includeSimulated) {
}
