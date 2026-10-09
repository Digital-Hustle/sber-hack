package ru.documents.docproc.api.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import ru.documents.docproc.api.dto.response.ModelStatusRs;
import ru.documents.docproc.api.dto.response.QueueStatsRs;
import ru.documents.docproc.api.dto.response.ResultRs;
import ru.documents.docproc.api.dto.response.TypeRs;

/**
 * API оператора docproc (docs/API.md «docproc»).
 */
@Tag(name = "docproc")
@RequestMapping("/api/v1")
public interface DocumentsController {

    /** Результат разбора. */
    @Operation(summary = "Результат разбора документа")
    @GetMapping("/documents/{id}/result")
    ResultRs result(@PathVariable UUID id);

    /** Разобрать заново (SENIOR_OPERATOR). */
    @Operation(summary = "Разобрать заново")
    @PostMapping("/documents/{id}/reprocess")
    ResponseEntity<Void> reprocess(@PathVariable UUID id);

    /** Состояние модели. */
    @Operation(summary = "Состояние модели")
    @GetMapping("/model/status")
    ModelStatusRs model();

    /** Состояние очереди. */
    @Operation(summary = "Состояние очереди")
    @GetMapping("/queue/stats")
    QueueStatsRs stats();

    /** Реестр видов. */
    @Operation(summary = "Виды документов")
    @GetMapping("/types")
    TypeRs types();
}
