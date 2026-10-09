package ru.documents.review.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.documents.review.api.dto.request.DecisionRq;
import ru.documents.review.api.dto.request.ResubmitRq;
import ru.documents.review.api.dto.request.RevealRq;
import ru.documents.review.api.dto.response.CardRs;
import ru.documents.review.api.dto.response.HistoryRs;
import ru.documents.review.api.dto.response.NextRs;
import ru.documents.review.api.dto.response.QueueRs;
import ru.documents.review.api.dto.response.RevealRs;

/**
 * Очередь и карточка заявки.
 */
@Tag(name = "applications")
@RequestMapping("/api/v1/applications")
public interface ApplicationsController {

    /** Очередь (FR-51). */
    @Operation(summary = "Очередь заявок с фильтрами и курсором")
    @GetMapping
    QueueRs queue(Authentication auth, @RequestParam(name = "status", required = false) List<String> status,
                  @RequestParam(name = "q", required = false) String q,
                  @RequestParam(name = "assignee", required = false) String assignee,
                  @RequestParam(name = "attention", defaultValue = "false") boolean attention,
                  @RequestParam(name = "sort", defaultValue = "waiting") String sort,
                  @RequestParam(name = "cursor", required = false) String cursor,
                  @RequestParam(name = "limit", defaultValue = "50") int limit);

    /** Взять следующую свободную. */
    @Operation(summary = "Взять следующую заявку")
    @PostMapping("/next")
    NextRs next(Authentication auth);

    /** Взять в работу; занято другим → 409 ALREADY_ASSIGNED. */
    @Operation(summary = "Назначить себе")
    @PostMapping("/{id}/assign")
    ResponseEntity<Void> assign(Authentication auth, @PathVariable UUID id,
                                @RequestParam(name = "force", defaultValue = "false") boolean force);

    /** Карточка (FR-52). */
    @Operation(summary = "Карточка заявки")
    @GetMapping("/{id}")
    CardRs card(@PathVariable UUID id);

    /** История (B-08). */
    @Operation(summary = "История заявки")
    @GetMapping("/{id}/history")
    HistoryRs history(Authentication auth, @PathVariable UUID id);

    /** Запрос «дослать/переснять» (FR-55). */
    @Operation(summary = "Запросить у соискателя документы")
    @PostMapping("/{id}/request")
    ResponseEntity<Void> request(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ResubmitRq rq);

    /** Решение (FR-53). */
    @Operation(summary = "Принять или вернуть заявку")
    @PostMapping("/{id}/decision")
    ResponseEntity<Void> decision(Authentication auth, @PathVariable UUID id, @Valid @RequestBody DecisionRq rq);

    /** Полный номер документа с записью в аудит (B-07). */
    @Operation(summary = "Показать полный номер")
    @PostMapping("/{id}/reveal")
    RevealRs reveal(Authentication auth, @PathVariable UUID id, @Valid @RequestBody RevealRq rq);

    /** Карточка соискателя для кадров (B-05). */
    @Operation(summary = "Выгрузить карточку соискателя (docx | xlsx)")
    @GetMapping("/{id}/export")
    ResponseEntity<byte[]> export(Authentication auth, @PathVariable UUID id,
                                  @RequestParam(name = "format", defaultValue = "docx") String format);
}
