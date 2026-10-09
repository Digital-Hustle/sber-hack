package ru.documents.review.features.workplace.model;

import java.util.UUID;

/**
 * Событие для SSE оператора.
 *
 * @param name          queue.changed | document.stage
 * @param applicationId заявка
 * @param documentId    документ
 * @param stage         стадия
 */
public record StreamEvent(String name, UUID applicationId, UUID documentId, String stage) {

    /**
     * Изменилась очередь.
     *
     * @param app заявка
     * @return событие
     */
    public static StreamEvent queue(UUID app) {
        return new StreamEvent("queue.changed", app, null, null);
    }
}
