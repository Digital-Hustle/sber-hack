package ru.proxodnaya.shift.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Напоминания рабочему ({@code shift.reminders.*}).
 *
 * @param exitAfter через сколько после конца смены напомнить отметить выход (B-04)
 */
@Validated
@ConfigurationProperties("shift.reminders")
public record ReminderProperties(@NotNull Duration exitAfter) {
}
