package ru.subsidy.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Единственная точка вызова языковой модели (День 5–6, docs/LLM.md).
 * Сервисы не знают о провайдерах: выбор — переменная LLM_PROVIDER.
 */
public interface LlmGateway {

    /** Ответ строго по JSON-схеме; невалидный ответ — LlmException. */
    ChatResult chatJson(List<ChatMessage> messages, JsonNode schema);

    /** Доступность модели для стадии WAITING_MODEL и индикатора в интерфейсе. */
    LlmStatus status();
}
