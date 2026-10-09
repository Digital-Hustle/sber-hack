package ru.proxodnaya.people.features.worker.model;

import java.time.Instant;
import java.util.UUID;

import ru.proxodnaya.people.shared.WorkerStatus;

/**
 * Рабочий.
 *
 * @param id         id (UUIDv7)
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 * @param status     статус
 * @param createdAt  создан
 * @param updatedAt  изменён
 */
public record Worker(UUID id, String fullName, String position, String contractor, WorkerStatus status,
                     Instant createdAt, Instant updatedAt) {
}
