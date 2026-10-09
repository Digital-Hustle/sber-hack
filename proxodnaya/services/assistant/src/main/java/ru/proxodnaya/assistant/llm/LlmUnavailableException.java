package ru.proxodnaya.assistant.llm;

/**
 * Провайдер LLM недоступен, не ответил вовремя или ответил не по формату.
 */
public class LlmUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
