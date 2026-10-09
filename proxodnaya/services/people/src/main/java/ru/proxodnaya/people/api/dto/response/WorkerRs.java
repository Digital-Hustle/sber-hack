package ru.proxodnaya.people.api.dto.response;

import java.time.Instant;

/**
 * Рабочий.
 *
 * @param id         id
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 * @param status     ACTIVE | BLOCKED
 * @param createdAt  создан
 * @param updatedAt  изменён
 */
public record WorkerRs(String id, String fullName, String position, String contractor, String status,
                       Instant createdAt, Instant updatedAt) {
}
