package ru.proxodnaya.shift.api.dto.response;

import java.time.Instant;

/**
 * Тикет для {@code EventSource}.
 *
 * @param ticket    значение для {@code ?ticket=}, одноразовое
 * @param stream    {@code situation} или {@code worker}
 * @param expiresAt до какого момента нужно открыть поток
 */
public record StreamTicketRs(String ticket, String stream, Instant expiresAt) {
}
