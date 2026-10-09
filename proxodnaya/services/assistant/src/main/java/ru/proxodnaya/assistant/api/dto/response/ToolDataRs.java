package ru.proxodnaya.assistant.api.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Сырые данные инструмента (при отказе из-за чисел).
 *
 * @param tool   инструмент
 * @param result данные
 */
public record ToolDataRs(String tool, JsonNode result) {
}
