package ru.proxodnaya.access.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Генератор демо-истории (FR-34, ADR-007), {@code access.simulator.*}.
 *
 * @param enabled    доступен ли {@code POST /simulator/runs}; на проде с реальными людьми — выключить
 * @param maxWorkers сколько рабочих за один прогон
 * @param maxDays    за сколько прошедших дней
 */
@Validated
@ConfigurationProperties("access.simulator")
public record SimulatorProperties(boolean enabled, @Min(1) @Max(SimulatorProperties.LIMIT) int maxWorkers,
                                  @Min(1) @Max(SimulatorProperties.LIMIT) int maxDays) {

    /** Верхняя граница настроек: прогон — одна транзакция. */
    public static final int LIMIT = 200;
}
