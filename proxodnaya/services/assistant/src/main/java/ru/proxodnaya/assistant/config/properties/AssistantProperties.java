package ru.proxodnaya.assistant.config.properties;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Настройки помощника ({@code assistant.*}).
 *
 * @param provider      провайдер LLM: {@code ollama} | {@code openai} (OpenAI-совместимый API)
 * @param baseUrl       адрес провайдера; для ollama — без {@code /v1}
 * @param apiKey        ключ (для ollama не нужен)
 * @param model         модель
 * @param timeout       таймаут одного вызова модели (FR-45)
 * @param maxIterations сколько раз модель может звать инструменты за один вопрос
 * @param shiftUrl      адрес shift
 * @param siteZone      пояс площадки: даты инструментов — дни в нём
 */
@Validated
@ConfigurationProperties("assistant")
public record AssistantProperties(@NotBlank String provider, @NotBlank String baseUrl, String apiKey, @NotBlank String model,
                                  @NotNull Duration timeout, @Min(1) @Max(AssistantProperties.MAX_ITERATIONS) int maxIterations,
                                  @NotBlank String shiftUrl, @NotNull ZoneId siteZone) {

    /** Больше итераций tool calling — дольше ответ без пользы. */
    public static final int MAX_ITERATIONS = 5;
}
