package ru.documents.review.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.documents.review.api.dto.response.TicketRs;

/**
 * SSE очереди (FR-57).
 */
@Tag(name = "stream")
@RequestMapping("/api/v1/stream")
public interface StreamController {

    /** Одноразовый билет на 30 с. */
    @Operation(summary = "Билет для EventSource")
    @GetMapping("/ticket")
    TicketRs ticket(Authentication auth);

    /** Поток событий: queue.changed, document.stage. */
    @Operation(summary = "Поток событий очереди")
    @GetMapping(produces = "text/event-stream")
    SseEmitter stream(@RequestParam("ticket") String ticket);
}
