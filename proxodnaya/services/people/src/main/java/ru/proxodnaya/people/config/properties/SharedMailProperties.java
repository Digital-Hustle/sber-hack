package ru.proxodnaya.people.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Общий почтовый клиент Digital Hustle: реквизиты SMTP находятся только у него.
 *
 * @param enabled использовать общий клиент
 * @param url внутренний адрес
 * @param token токен внутреннего API
 * @param timeout таймаут
 */
@ConfigurationProperties("people.shared-mail")
public record SharedMailProperties(boolean enabled, String url, String token, Duration timeout) {
}
