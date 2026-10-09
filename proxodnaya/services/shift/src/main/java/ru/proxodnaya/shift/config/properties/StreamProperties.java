package ru.proxodnaya.shift.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Живой поток SSE ({@code shift.stream.*}, SH-08).
 *
 * @param ticketTtl сколько живёт одноразовый тикет
 * @param heartbeat период комментария-пульса (держит соединение через прокси)
 * @param maxAge    через сколько сервер закрывает соединение (браузер переподключится с {@code Last-Event-ID})
 * @param buffer    сколько последних событий хранится для досылки по {@code Last-Event-ID}
 */
@Validated
@ConfigurationProperties("shift.stream")
public record StreamProperties(@NotNull Duration ticketTtl, @NotNull Duration heartbeat, @NotNull Duration maxAge,
                               @Positive int buffer) {
}
