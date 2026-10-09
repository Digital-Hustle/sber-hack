package ru.proxodnaya.assistant.features.assistant.application.tools;

/**
 * Модель передала аргументы не по схеме инструмента.
 */
public class ToolArgumentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ToolArgumentException(String message) {
        super(message);
    }
}
