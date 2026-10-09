package ru.proxodnaya.assistant.api.dto.response;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Таблица к ответу: значения — из данных инструмента.
 *
 * @param tool    инструмент
 * @param columns столбцы
 * @param rows    строки
 */
public record TableRs(String tool, List<String> columns, List<List<JsonNode>> rows) {
}
