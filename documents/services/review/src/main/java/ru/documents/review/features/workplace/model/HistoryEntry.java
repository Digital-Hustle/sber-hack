package ru.documents.review.features.workplace.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Запись истории заявки (B-08).
 *
 * @param id         запись
 * @param documentId документ
 * @param actor      кто
 * @param source     OPERATOR | MODEL | APPLICANT | SYSTEM
 * @param action     действие
 * @param details    детали (поле, было, стало…)
 * @param createdAt  когда
 */
public record HistoryEntry(UUID id, UUID documentId, String actor, String source, String action, JsonNode details,
                           Instant createdAt) {
}
