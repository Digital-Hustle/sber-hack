package ru.proxodnaya.shift.api.controller;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.shift.api.constants.PathConstants;
import ru.proxodnaya.shift.api.dto.response.StreamTicketRs;

/**
 * Живые обновления (SSE, SH-08). {@code EventSource} не шлёт заголовок Authorization, поэтому поток открывается
 * по одноразовому тикету, полученному с токеном.
 */
@Tag(name = "Stream", description = "Живые обновления (SSE)")
@RequestMapping(PathConstants.STREAM)
public interface StreamController {

    /** Заголовок досылки пропущенного. */
    String LAST_EVENT_ID = "Last-Event-ID";

    /**
     * Тикет на поток: рабочему — на свой поток, персоналу — на обстановку.
     *
     * @param authentication токен
     * @return тикет на {@code shift.stream.ticket-ttl} (30 с)
     */
    @Operation(summary = "Тикет на поток")
    @GetMapping(PathConstants.TICKET)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER', 'GUARD', 'WORKER')")
    StreamTicketRs ticket(@Parameter(hidden = true) Authentication authentication);

    /**
     * Обстановка: {@code presence.changed}, {@code attempt.created}.
     *
     * @param ticket      тикет персонала
     * @param lastEventId последнее полученное событие (браузер шлёт сам при переподключении)
     * @return поток
     */
    @Operation(summary = "Поток обстановки")
    @GetMapping(path = PathConstants.SITUATION, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter situation(@RequestParam String ticket, @RequestHeader(name = LAST_EVENT_ID, required = false) String lastEventId);

    /**
     * Поток рабочего: {@code attempt.result} по своим проходам.
     *
     * @param ticket      тикет рабочего
     * @param lastEventId последнее полученное событие
     * @return поток
     */
    @Operation(summary = "Поток рабочего")
    @GetMapping(path = PathConstants.WORKER_STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter worker(@RequestParam String ticket, @RequestHeader(name = LAST_EVENT_ID, required = false) String lastEventId);
}
