package ru.proxodnaya.access.api.dto.response;

/**
 * Кто проходит.
 *
 * @param id         рабочий
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 */
public record WorkerRs(String id, String fullName, String position, String contractor) {
}
