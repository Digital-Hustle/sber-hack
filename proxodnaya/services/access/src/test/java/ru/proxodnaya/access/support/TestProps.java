package ru.proxodnaya.access.support;

import java.time.Duration;
import java.time.ZoneId;

import ru.proxodnaya.access.config.properties.AccessProperties;
import ru.proxodnaya.access.features.settings.api.PassTuning;

/**
 * Настройки как в application.yml.
 */
public final class TestProps {

    private TestProps() {
    }

    /**
     * Настройки по умолчанию.
     *
     * @return настройки
     */
    public static AccessProperties defaults() {
        return new AccessProperties(Duration.ofSeconds(90), Duration.ofSeconds(30), Duration.ofHours(16), Duration.ofSeconds(10),
                3, 5, Duration.ofMinutes(10), ZoneId.of("Europe/Moscow"),
                new AccessProperties.Deps("http://face", "http://people", "http://shift", Duration.ofMillis(300)));
    }

    /**
     * Параметры прохода как в env, порог лица не строже face, смена обязательна.
     *
     * @return параметры
     */
    public static PassTuning tuning() {
        return tuning(0.0, true);
    }

    /**
     * Параметры прохода с заданными порогом лица и требованием смены.
     *
     * @param faceThreshold порог
     * @param requireShift  требовать смену
     * @return параметры
     */
    public static PassTuning tuning(double faceThreshold, boolean requireShift) {
        AccessProperties props = defaults();
        return new PassTuning() {
            @Override
            public Duration qrTolerance() {
                return props.qrTolerance();
            }

            @Override
            public Duration repeatScanCooldown() {
                return props.repeatScanCooldown();
            }

            @Override
            public Duration presenceTtl() {
                return props.presenceTtl();
            }

            @Override
            public int tempLockFailures() {
                return props.tempLockFailures();
            }

            @Override
            public Duration tempLockWindow() {
                return props.tempLockWindow();
            }

            @Override
            public double faceThreshold() {
                return faceThreshold;
            }

            @Override
            public boolean requireShift() {
                return requireShift;
            }
        };
    }
}
