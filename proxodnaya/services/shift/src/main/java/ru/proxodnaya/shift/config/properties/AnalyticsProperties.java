package ru.proxodnaya.shift.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Пороги аналитики опозданий и переработок ({@code shift.analytics.*}).
 *
 * @param lateGrace         вход позже начала смены не больше чем на это — не опоздание
 * @param overtimeThreshold выход позже конца смены меньше чем на это — не переработка
 */
@Validated
@ConfigurationProperties("shift.analytics")
public record AnalyticsProperties(@NotNull Duration lateGrace, @NotNull Duration overtimeThreshold) {
}
