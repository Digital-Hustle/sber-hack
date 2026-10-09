package ru.subsidy.llm;

/** Ошибка вызова модели; unavailable=true — файл уходит в WAITING_MODEL, а не в FAILED. */
public class LlmException extends RuntimeException {

    private final boolean unavailable;

    public LlmException(String message, boolean unavailable, Throwable cause) {
        super(message, cause);
        this.unavailable = unavailable;
    }

    public boolean isUnavailable() {
        return unavailable;
    }
}
