package ru.proxodnaya.shift.features.report.query.model;

import java.util.List;

/**
 * Строка табеля.
 *
 * @param person     кто
 * @param days       клетки по дням периода, по порядку
 * @param daysWorked дней с кодом Т
 * @param minutes    всего минут
 * @param absences   дней с кодом Н
 * @param incomplete дней с неполными отметками
 */
public record TimesheetRow(TimesheetPerson person, List<TimesheetDay> days, int daysWorked, long minutes, int absences,
                           int incomplete) {
}
