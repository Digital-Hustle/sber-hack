package ru.documents.docproc.features.processing.model;

import java.util.List;

/**
 * Итог по полю.
 *
 * @param name       поле
 * @param title      название
 * @param value      нормализованное значение
 * @param raw        как в документе
 * @param page       страница
 * @param bbox       [x0, y0, x1, y1] в пикселях страницы
 * @param confidence уверенность 0..1 (null — значения нет)
 * @param checks     проверки
 * @param reason     почему нет значения
 */
public record FieldResult(String name, String title, String value, String raw, Integer page, List<Integer> bbox,
                          Double confidence, List<Check> checks, String reason) {

    /**
     * Проверка поля.
     *
     * @param code код
     * @param ok   пройдена
     */
    public record Check(String code, boolean ok) {
    }
}
