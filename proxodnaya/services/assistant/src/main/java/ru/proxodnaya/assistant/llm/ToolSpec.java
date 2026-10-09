package ru.proxodnaya.assistant.llm;

import java.util.Map;

/**
 * Описание инструмента для модели.
 *
 * @param name        имя
 * @param description что делает
 * @param parameters  JSON-схема аргументов
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {
}
