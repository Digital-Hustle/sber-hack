package ru.documents.intake.features.applications.model;

import java.util.List;
import java.util.UUID;

/**
 * Принятый файл.
 *
 * @param documentId  документ
 * @param fileId      файл
 * @param stage       RECEIVED
 * @param duplicateOf похожий документ (почти-дубликат)
 * @param warnings    предупреждения
 */
public record Uploaded(UUID documentId, UUID fileId, String stage, UUID duplicateOf, List<String> warnings) {
}
