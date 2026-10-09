package ru.documents.review.features.workplace.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Заявка в проекции.
 *
 * @param id            заявка
 * @param status        DRAFT | SUBMITTED | IN_REVIEW | NEEDS_CHANGES | ACCEPTED | RETURNED
 * @param source        WEB | DEMO
 * @param requiredTypes обязательные виды по анкете
 * @param missing       чего не хватало при отправке
 * @param applicantName ФИО из документов
 * @param assignee      кто взял в работу
 * @param assignedAt    когда
 * @param createdAt     создана
 * @param submittedAt   отправлена (последний раз)
 * @param decidedAt     решение
 * @param updatedAt     обновлена
 */
public record AppRow(UUID id, String status, String source, List<String> requiredTypes, List<String> missing,
                     String applicantName, String assignee, Instant assignedAt, Instant createdAt, Instant submittedAt,
                     Instant decidedAt, Instant updatedAt) {

    /** Решение уже принято. */
    public boolean decided() {
        return "ACCEPTED".equals(status) || "RETURNED".equals(status);
    }
}
