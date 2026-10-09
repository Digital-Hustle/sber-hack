package ru.documents.docproc.api.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import ru.documents.docproc.api.dto.request.RetypeRq;
import ru.documents.docproc.api.dto.response.ResultRs;

/**
 * Внутренний API для review (service account svc-review).
 */
@Hidden
@RequestMapping("/internal/docproc")
public interface InternalController {

    /** Результат для карточки review. */
    @GetMapping("/documents/{id}/result")
    ResultRs result(@PathVariable UUID id);

    /** Смена вида → переизвлечение полей. */
    @PostMapping("/documents/{id}/retype")
    ResponseEntity<Void> retype(@PathVariable UUID id, @Valid @RequestBody RetypeRq rq);

    /** Разобрать заново по запросу review. */
    @PostMapping("/documents/{id}/reprocess")
    ResponseEntity<Void> reprocess(@PathVariable UUID id);
}
