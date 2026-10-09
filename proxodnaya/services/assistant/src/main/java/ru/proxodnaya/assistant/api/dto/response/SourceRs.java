package ru.proxodnaya.assistant.api.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Источник чисел ответа.
 *
 * @param tool инструмент
 * @param args аргументы
 * @param at   момент данных
 * @param ok   получены ли данные
 */
public record SourceRs(String tool, JsonNode args, Instant at, boolean ok) {
}
