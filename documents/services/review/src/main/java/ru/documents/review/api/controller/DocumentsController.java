package ru.documents.review.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.documents.review.api.dto.request.FieldsRq;
import ru.documents.review.api.dto.request.TypeRq;

/**
 * Действия с документом.
 */
@Tag(name = "documents")
@RequestMapping("/api/v1/documents")
public interface DocumentsController {

    /** Правка полей (FR-53). */
    @Operation(summary = "Исправить поля")
    @PatchMapping("/{id}/fields")
    ResponseEntity<Void> fields(Authentication auth, @PathVariable UUID id, @Valid @RequestBody FieldsRq rq);

    /** Смена вида → переизвлечение полей по новой схеме. */
    @Operation(summary = "Сменить вид документа")
    @PatchMapping("/{id}/type")
    ResponseEntity<Void> type(Authentication auth, @PathVariable UUID id, @Valid @RequestBody TypeRq rq);

    /** Подтвердить документ. */
    @Operation(summary = "Подтвердить документ")
    @PostMapping("/{id}/confirm")
    ResponseEntity<Void> confirm(Authentication auth, @PathVariable UUID id);

    /** Разобрать заново (старший оператор). */
    @Operation(summary = "Разобрать заново")
    @PostMapping("/{id}/reprocess")
    ResponseEntity<Void> reprocess(Authentication auth, @PathVariable UUID id);
}
