package ru.proxodnaya.shift.features.report.query.model;

/**
 * Клетка табеля: человек × день.
 *
 * @param mark       код
 * @param minutes    минут на объекте в этот день
 * @param incomplete хотя бы один интервал без отмеченного выхода
 */
public record TimesheetDay(DayMark mark, long minutes, boolean incomplete) {
}
