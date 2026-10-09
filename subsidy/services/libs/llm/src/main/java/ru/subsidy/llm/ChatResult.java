package ru.subsidy.llm;

import com.fasterxml.jackson.databind.JsonNode;

/** Результат вызова: разобранный JSON и метаданные для журнала llm_calls (без текста документа). */
public record ChatResult(JsonNode json, String provider, String model, long latencyMs, int promptTokens, int outputTokens) {}
