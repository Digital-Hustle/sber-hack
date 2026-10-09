package ru.documents.docproc.features.processing.model;

import java.util.Map;

/**
 * Причина/замечание по документу (docs/API.md «Причины непригодности и замечания»).
 *
 * @param code   код
 * @param params параметры текста
 */
public record Finding(String code, Map<String, String> params) {

    /**
     * Без параметров.
     *
     * @param code код
     * @return замечание
     */
    public static Finding of(String code) {
        return new Finding(code, Map.of());
    }
}
