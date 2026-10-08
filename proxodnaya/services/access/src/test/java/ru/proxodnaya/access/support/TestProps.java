package ru.proxodnaya.access.support;

import java.time.Duration;
import java.time.ZoneId;

import ru.proxodnaya.access.config.properties.AccessProperties;

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
}
