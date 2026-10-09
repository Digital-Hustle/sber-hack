package ru.subsidy.llm;

/** Состояние модели для health и интерфейса. */
public record LlmStatus(boolean available, String provider, String model, String reason) {}
