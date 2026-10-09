package ru.proxodnaya.assistant.features.assistant.model;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Откуда числа ответа (FR-44).
 *
 * @param tool   инструмент
 * @param args   аргументы
 * @param at     момент данных
 * @param ok     получены ли данные
 * @param result данные или ошибка
 */
public record Source(String tool, JsonNode args, Instant at, boolean ok, JsonNode result) {
}
