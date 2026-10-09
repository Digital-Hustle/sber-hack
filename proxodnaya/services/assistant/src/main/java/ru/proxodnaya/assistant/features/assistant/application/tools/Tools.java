package ru.proxodnaya.assistant.features.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Разбор аргументов инструментов.
 */
final class Tools {

    private static final int MAX_ID = 64;

    private Tools() {
    }

    /**
     * Необязательная строка.
     *
     * @param args аргументы
     * @param name имя
     * @return значение или {@code null}
     * @throws ToolArgumentException если слишком длинная
     */
    static String text(JsonNode args, String name) {
        JsonNode value = args == null ? null : args.get(name);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        String text = value.asText().strip();
        if (text.length() > MAX_ID) {
            throw new ToolArgumentException(name + " слишком длинный");
        }
        return text;
    }
}
