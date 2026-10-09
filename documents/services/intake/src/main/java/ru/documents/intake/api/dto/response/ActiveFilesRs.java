package ru.documents.intake.api.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Активные файлы документа (для docproc), по порядку страниц.
 *
 * @param items файлы
 */
public record ActiveFilesRs(List<Item> items) {

    /**
     * Файл.
     *
     * @param fileId файл
     * @param mime   тип
     * @param pages  страниц
     */
    public record Item(UUID fileId, String mime, int pages) {
    }
}
