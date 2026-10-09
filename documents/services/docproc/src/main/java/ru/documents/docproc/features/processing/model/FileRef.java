package ru.documents.docproc.features.processing.model;

import java.util.UUID;

/**
 * Активный файл документа в intake.
 *
 * @param fileId файл
 * @param mime   тип
 * @param pages  страниц
 */
public record FileRef(UUID fileId, String mime, int pages) {
}
