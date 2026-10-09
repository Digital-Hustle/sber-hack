package ru.proxodnaya.shift.api.controller.impl;

import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ru.proxodnaya.shift.api.controller.StreamController;
import ru.proxodnaya.shift.api.dto.response.StreamTicketRs;
import ru.proxodnaya.shift.features.stream.command.service.SseHub;
import ru.proxodnaya.shift.features.stream.command.service.TicketService;
import ru.proxodnaya.shift.features.stream.model.StreamKind;
import ru.proxodnaya.shift.features.stream.model.StreamTicket;

/**
 * Реализация API потоков.
 */
@RestController
public class StreamControllerImpl implements StreamController {

    private static final String ROLE_WORKER = "ROLE_WORKER";

    private final TicketService tickets;
    private final SseHub hub;

    public StreamControllerImpl(TicketService tickets, SseHub hub) {
        this.tickets = tickets;
        this.hub = hub;
    }

    @Override
    public StreamTicketRs ticket(Authentication authentication) {
        boolean worker = authentication.getAuthorities().stream().anyMatch(role -> ROLE_WORKER.equals(role.getAuthority()));
        StreamTicket ticket = tickets.issue(worker ? StreamKind.WORKER : StreamKind.SITUATION, authentication.getName());
        return new StreamTicketRs(ticket.value(), ticket.kind().name().toLowerCase(Locale.ROOT), ticket.expiresAt());
    }

    @Override
    public SseEmitter situation(String ticket, String lastEventId) {
        tickets.redeem(ticket, StreamKind.SITUATION);
        return hub.subscribe(StreamKind.SITUATION, null, lastEventId);
    }

    @Override
    public SseEmitter worker(String ticket, String lastEventId) {
        StreamTicket redeemed = tickets.redeem(ticket, StreamKind.WORKER);
        return hub.subscribe(StreamKind.WORKER, redeemed.subject(), lastEventId);
    }
}
