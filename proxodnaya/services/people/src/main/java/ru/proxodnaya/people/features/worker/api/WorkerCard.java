package ru.proxodnaya.people.features.worker.api;

import java.util.UUID;

import ru.proxodnaya.people.shared.WorkerStatus;

/**
 * Карточка рабочего для других фич.
 *
 * @param id         id
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 * @param status     статус
 */
public record WorkerCard(UUID id, String fullName, String position, String contractor, WorkerStatus status) {
}
