package ru.proxodnaya.people.features.worker.model;

/**
 * Данные для создания или изменения рабочего; {@code null} в изменении — поле не трогаем.
 *
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 */
public record WorkerDraft(String fullName, String position, String contractor) {
}
