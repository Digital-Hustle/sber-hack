package ru.documents.intake.api.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Активные файлы документа (для docproc), по порядку страниц.
 *
 * @param items     файлы
 * @param onlyPages разбирать только эти страницы (сквозные номера по файлам) — документ выделен из многостраничного
 *                  файла (FR-07); null — все страницы
 */
public record ActiveFilesRs(List<Item> items, List<Integer> onlyPages) {

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
