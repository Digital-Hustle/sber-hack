package ru.proxodnaya.shift.features.stream.command.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.shift.config.properties.StreamProperties;
import ru.proxodnaya.shift.features.stream.model.StreamKind;
import ru.proxodnaya.shift.features.stream.model.StreamTicket;

/**
 * Одноразовые тикеты SSE (SH-08). Хранятся в памяти: экземпляр shift один, тикет живёт секунды — после рестарта
 * клиент просто берёт новый.
 */
@Service
public class TicketService {

    private static final int TICKET_BYTES = 32;
    private static final int MAX_TICKETS = 10_000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, StreamTicket> tickets = new ConcurrentHashMap<>();
    private final StreamProperties props;
    private final Clock clock;

    public TicketService(StreamProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /**
     * Выдаёт тикет.
     *
     * @param kind    поток
     * @param subject {@code sub} токена
     * @return тикет
     */
    public StreamTicket issue(StreamKind kind, String subject) {
        Instant now = clock.instant();
        if (tickets.size() >= MAX_TICKETS) {
            tickets.values().removeIf(ticket -> !ticket.expiresAt().isAfter(now));
        }
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        StreamTicket ticket = new StreamTicket(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), kind, subject,
                now.plus(props.ticketTtl()));
        tickets.put(ticket.value(), ticket);
        return ticket;
    }

    /**
     * Погашает тикет: второй раз, чужой поток или после срока — отказ.
     *
     * @param value значение из {@code ?ticket=}
     * @param kind  поток, который открывают
     * @return тикет
     * @throws DomainException {@code FORBIDDEN}, если тикет не годится
     */
    public StreamTicket redeem(String value, StreamKind kind) {
        StreamTicket ticket = value == null ? null : tickets.remove(value);
        if (ticket == null || !ticket.expiresAt().isAfter(clock.instant()) || ticket.kind() != kind) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Тикет потока недействителен или истёк — получите новый");
        }
        return ticket;
    }
}
