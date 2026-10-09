package ru.proxodnaya.shift.features.report.query.model;

/**
 * Кто в строке табеля; без карточки из people — {@code fullName} равен id.
 *
 * @param workerId   рабочий
 * @param fullName   ФИО
 * @param position   должность или {@code null}
 * @param contractor подрядчик или {@code null}
 */
public record TimesheetPerson(String workerId, String fullName, String position, String contractor) {
}
