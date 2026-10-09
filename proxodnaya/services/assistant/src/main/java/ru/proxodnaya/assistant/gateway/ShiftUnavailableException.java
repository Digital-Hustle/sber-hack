package ru.proxodnaya.assistant.gateway;

/**
 * Сервис shift не дал данные инструменту.
 */
public class ShiftUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ShiftUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
