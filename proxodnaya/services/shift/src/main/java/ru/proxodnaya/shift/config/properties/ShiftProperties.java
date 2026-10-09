package ru.proxodnaya.shift.config.properties;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Настройки shift ({@code shift.*} в application.yml).
 *
 * @param siteZone       часовой пояс площадки: дни, недели и время смен считаются в нём, хранение — UTC
 * @param earlyEntry     насколько раньше начала смены можно войти
 * @param autoCloseAfter через сколько после конца смены незакрытый интервал закрывается {@code AUTO_SHIFT_END}
 * @param presenceTtl    вход старше этого без выхода — человек не считается «на объекте» (как в access, ADR-037)
 */
@Validated
@ConfigurationProperties("shift")
public record ShiftProperties(
        @NotNull ZoneId siteZone,
        @NotNull Duration earlyEntry,
        @NotNull Duration autoCloseAfter,
        @NotNull Duration presenceTtl
) {
}
