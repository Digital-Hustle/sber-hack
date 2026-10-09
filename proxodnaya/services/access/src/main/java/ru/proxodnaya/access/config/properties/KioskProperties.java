package ru.proxodnaya.access.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Терминалы (ACC-12), {@code access.kiosk.*}.
 *
 * @param clockSkew    допустимое расхождение часов терминала и сервера ({@code X-Ts})
 * @param pathPrefix   префикс пути, который срезает gateway: терминал подписывает путь целиком ({@code /access/api/v1/...})
 * @param waitingShown сколько показывать в кабинете несопряжённый терминал после его последнего пульса
 */
@ConfigurationProperties("access.kiosk")
public record KioskProperties(Duration clockSkew, String pathPrefix, Duration waitingShown) {
}
