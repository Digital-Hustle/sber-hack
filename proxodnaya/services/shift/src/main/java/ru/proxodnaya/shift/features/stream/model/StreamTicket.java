package ru.proxodnaya.shift.features.stream.model;

import java.time.Instant;

/**
 * Одноразовый тикет для {@code EventSource} (браузер не умеет слать заголовок Authorization).
 *
 * @param value     значение (в URL {@code ?ticket=})
 * @param kind      на какой поток
 * @param subject   кто получил ({@code sub}): для WORKER — сам рабочий
 * @param expiresAt до какого момента действует
 */
public record StreamTicket(String value, StreamKind kind, String subject, Instant expiresAt) {
}
