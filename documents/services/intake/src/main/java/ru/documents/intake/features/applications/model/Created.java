package ru.documents.intake.features.applications.model;

import java.util.UUID;

/**
 * Новая заявка: ключ и код показываются один раз.
 *
 * @param applicationId заявка
 * @param accessKey     ключ заявки
 * @param resumeCode    код возврата
 * @param link          ссылка возврата
 */
public record Created(UUID applicationId, String accessKey, String resumeCode, String link) {
}
