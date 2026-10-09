package ru.documents.review.api.controller.impl;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.documents.common.exception.DomainException;
import ru.documents.review.api.controller.StreamController;
import ru.documents.review.api.dto.response.TicketRs;
import ru.documents.review.features.workplace.application.service.StreamHub;
import ru.documents.review.features.workplace.command.service.TicketService;
import ru.documents.review.features.workplace.model.Actor;

/**
 * SSE очереди.
 */
@RestController
public class StreamControllerImpl implements StreamController {

    private final TicketService tickets;
    private final StreamHub hub;

    public StreamControllerImpl(TicketService tickets, StreamHub hub) {
        this.tickets = tickets;
        this.hub = hub;
    }

    @Override
    public TicketRs ticket(Authentication auth) {
        return new TicketRs(tickets.issue(Actor.of(auth).username()));
    }

    @Override
    public SseEmitter stream(String ticket) {
        tickets.consume(ticket).orElseThrow(() -> new DomainException("TICKET_INVALID", 401,
                "Билет недействителен или истёк", "Получите новый билет"));
        return hub.subscribe();
    }
}
