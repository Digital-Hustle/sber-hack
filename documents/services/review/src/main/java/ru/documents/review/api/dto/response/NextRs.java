package ru.documents.review.api.dto.response;

import java.util.UUID;

/**
 * Назначенная заявка.
 *
 * @param applicationId заявка
 */
public record NextRs(UUID applicationId) {
}
