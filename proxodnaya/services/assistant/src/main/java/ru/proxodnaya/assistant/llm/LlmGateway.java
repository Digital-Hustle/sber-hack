package ru.proxodnaya.assistant.llm;

/**
 * Порт к LLM (ADR-005): адаптеры — только в {@code llm.infra}.
 */
public interface LlmGateway {

    /**
     * Один шаг диалога.
     *
     * @param request диалог и инструменты
     * @return ответ
     * @throws LlmUnavailableException если провайдер недоступен или не ответил за таймаут
     */
    ChatRs chat(ChatRq request);

    /**
     * Провайдер.
     *
     * @return имя
     */
    String provider();

    /**
     * Модель.
     *
     * @return имя
     */
    String model();
}
