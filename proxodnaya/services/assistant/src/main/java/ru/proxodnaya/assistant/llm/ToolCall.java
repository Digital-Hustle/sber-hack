package ru.proxodnaya.assistant.llm;

/**
 * Вызов инструмента, запрошенный моделью.
 *
 * @param id        id вызова
 * @param name      инструмент
 * @param arguments аргументы JSON-строкой
 */
public record ToolCall(String id, String name, String arguments) {
}
