package ru.proxodnaya.shift.features.shift.model;

import ru.proxodnaya.shift.shared.Period;

/**
 * Фильтр списка смен: рабочий и период по началу смены.
 *
 * @param workerId рабочий или {@code null}
 * @param period   период по началу
 */
public record ShiftFilter(String workerId, Period period) {
}
