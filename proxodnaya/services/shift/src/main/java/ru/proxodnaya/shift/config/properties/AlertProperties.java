package ru.proxodnaya.shift.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Правила оповещений B-01/B-05 ({@code shift.alerts.*}).
 *
 * @param siteLat       широта центра площадки или {@code null} — геозона выключена
 * @param siteLon       долгота центра площадки
 * @param siteRadius    радиус геозоны, м
 * @param noSignalAfter нет heartbeat дольше — «нет связи»
 * @param crowdLimit    больше стольких человек в зоне — скопление; 0 — правило выключено
 * @param crowdFor      сколько должно держаться скопление
 */
@Validated
@ConfigurationProperties("shift.alerts")
public record AlertProperties(Double siteLat, Double siteLon, @Min(1) int siteRadius, @NotNull Duration noSignalAfter,
                              @Min(0) int crowdLimit, @NotNull Duration crowdFor) {

    /**
     * Задана ли геозона.
     *
     * @return {@code true}, если есть обе координаты
     */
    public boolean fenceEnabled() {
        return siteLat != null && siteLon != null;
    }
}
