package ru.subsidy.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки модели из env (docs/LLM.md); ключи только из окружения. */
@ConfigurationProperties("app.llm")
public record LlmProperties(String provider, String baseUrl, String model, String visionModel,
                            String apiKey, Integer timeoutSeconds, Integer maxConcurrency) {}
