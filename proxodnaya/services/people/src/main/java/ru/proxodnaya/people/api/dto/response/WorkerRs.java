package ru.proxodnaya.people.api.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Рабочий.
 *
 * @param id          id
 * @param fullName    ФИО
 * @param position    должность
 * @param contractor  подрядчик
 * @param status      ACTIVE | BLOCKED
 * @param zoneIds     зоны допуска
 * @param permits     допуски со сроком
 * @param permitUntil ближайший срок окончания допусков ({@code null} — допусков нет)
 * @param faceEnrolledAt когда заведён эталон лица ({@code null} — нет)
 * @param createdAt   создан
 * @param updatedAt   изменён
 */
public record WorkerRs(String id, String fullName, String position, String contractor, String status,
                       List<String> zoneIds, List<PermitRs> permits, LocalDate permitUntil, Instant faceEnrolledAt,
                       Instant createdAt, Instant updatedAt) {
}
