package ru.documents.docproc.features.processing.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Итог разбора документа.
 *
 * @param stage          PARSED | NEEDS_REVIEW | UNREADABLE | FAILED
 * @param type           вид или UNKNOWN
 * @param typeConfidence уверенность вида
 * @param evidence       фрагменты-основания вида
 * @param findings       причины и замечания
 * @param fields         поля
 * @param quality        метрики качества по страницам
 * @param mrz            MRZ
 * @param pages          размеры страниц
 * @param model          модель
 * @param promptVersion  версия промптов
 * @param schemaVersion  версия схемы типа
 * @param durations      мс по стадиям
 */
public record FinalResult(String stage, String type, Double typeConfidence, List<String> evidence,
                          List<Finding> findings, List<FieldResult> fields, JsonNode quality, JsonNode mrz,
                          JsonNode pages, String model, String promptVersion, Integer schemaVersion,
                          Map<String, Long> durations) {
}
