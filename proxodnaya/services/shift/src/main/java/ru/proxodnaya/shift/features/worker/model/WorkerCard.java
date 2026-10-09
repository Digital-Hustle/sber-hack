package ru.proxodnaya.shift.features.worker.model;

/**
 * Карточка рабочего из {@code people.worker.v1}; поля, которых нет в событии, — {@code null}.
 *
 * @param workerId   рабочий
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 * @param status     ACTIVE | BLOCKED | DELETED
 */
public record WorkerCard(String workerId, String fullName, String position, String contractor, String status) {
}
