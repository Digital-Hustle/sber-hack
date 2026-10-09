package ru.documents.review.features.workplace.rules;

import java.util.Map;
import java.util.UUID;

/**
 * Итоговые значения полей одного документа (правка оператора ?? модель).
 *
 * @param id       документ
 * @param type     вид
 * @param typeName название вида
 * @param values   поле → значение
 */
public record DocFacts(UUID id, String type, String typeName, Map<String, String> values) {

    /**
     * Значение поля или null.
     *
     * @param name поле
     * @return значение
     */
    public String get(String name) {
        String v = values.get(name);
        return v == null || v.isBlank() ? null : v;
    }

    /**
     * ФИО одной строкой независимо от вида документа.
     *
     * @return ФИО или null
     */
    public String fullName() {
        if (get("fullName") != null) {
            return get("fullName");
        }
        String given = get("givenName") != null ? get("givenName") : get("givenNames");
        String joined = String.join(" ", java.util.stream.Stream.of(get("surname"), given, get("patronymic"))
                .filter(java.util.Objects::nonNull).toList());
        return joined.isBlank() ? null : joined;
    }
}
