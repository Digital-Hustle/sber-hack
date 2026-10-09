package ru.proxodnaya.assistant.features.assistant.model;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Таблица к ответу (FR-44): строит сервер из данных инструмента, а не модель.
 *
 * @param tool    инструмент-источник
 * @param columns поля строк
 * @param rows    строки в порядке столбцов
 */
public record Table(String tool, List<String> columns, List<List<JsonNode>> rows) {
}
