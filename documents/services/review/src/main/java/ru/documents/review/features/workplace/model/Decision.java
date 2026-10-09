package ru.documents.review.features.workplace.model;

import java.time.Instant;

/**
 * Решение по заявке.
 *
 * @param decision  ACCEPTED | RETURNED
 * @param comment   комментарий
 * @param actor     кто
 * @param createdAt когда
 */
public record Decision(String decision, String comment, String actor, Instant createdAt) {
}
