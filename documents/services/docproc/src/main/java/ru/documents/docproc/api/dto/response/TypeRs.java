package ru.documents.docproc.api.dto.response;

import java.util.List;

/**
 * Виды документов для выбора оператором.
 *
 * @param items виды
 */
public record TypeRs(List<Item> items) {

    /**
     * Вид.
     *
     * @param code    код
     * @param name    название
     * @param version версия схемы
     */
    public record Item(String code, String name, int version) {
    }
}
