package ru.documents.docproc.features.processing.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Вид документа из реестра doc_types.
 *
 * @param code    код
 * @param name    название
 * @param signs   признаки для классификации
 * @param schema  JSON Schema полей
 * @param version версия схемы
 */
public record DocType(String code, String name, String signs, JsonNode schema, int version) {
}
