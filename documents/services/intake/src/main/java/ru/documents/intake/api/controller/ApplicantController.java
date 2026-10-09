package ru.documents.intake.api.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ru.documents.intake.api.dto.request.CreateApplicationRq;
import ru.documents.intake.api.dto.request.ProfileRq;
import ru.documents.intake.api.dto.request.ResumeRq;
import ru.documents.intake.api.dto.response.RequiredDocumentsRs;
import ru.documents.intake.api.dto.response.SubmitRs;
import ru.documents.intake.api.dto.response.TicketRs;
import ru.documents.intake.features.applications.model.ApplicationView;
import ru.documents.intake.features.applications.model.Created;
import ru.documents.intake.features.applications.model.Uploaded;
import ru.documents.intake.features.applications.rules.Questionnaire.Question;
import ru.documents.intake.security.ApplicantPrincipal;

/**
 * Кабинет соискателя (docs/API.md «Соискатель»).
 */
@Tag(name = "applicant")
@RequestMapping("/api/v1/applicant")
public interface ApplicantController {

    /** Вопросы анкеты. */
    @GetMapping("/questionnaire")
    java.util.Map<String, java.util.List<Question>> questionnaire();

    /** Новая заявка. */
    @PostMapping("/applications")
    ResponseEntity<Created> create(@RequestBody CreateApplicationRq rq);

    /** Продолжить по коду. */
    @PostMapping("/resume")
    Created resume(@Valid @RequestBody ResumeRq rq);

    /** Заявка. */
    @GetMapping("/applications/{id}")
    ApplicationView get(@AuthenticationPrincipal ApplicantPrincipal me, @PathVariable UUID id);

    /** Анкета → набор. */
    @PutMapping("/applications/{id}/profile")
    RequiredDocumentsRs profile(@AuthenticationPrincipal ApplicantPrincipal me, @PathVariable UUID id,
                                @Valid @RequestBody ProfileRq rq);

    /** Загрузка файла: 202 сразу, разбор в фоне. */
    @PostMapping(value = "/applications/{id}/documents", consumes = "multipart/form-data")
    ResponseEntity<Uploaded> upload(@AuthenticationPrincipal ApplicantPrincipal me, @PathVariable UUID id,
                                    @RequestPart("file") MultipartFile file,
                                    @RequestParam(value = "slotType", required = false) String slotType,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key);

    /** Дозагрузка страницы / замена. */
    @PostMapping(value = "/applications/{id}/documents/{docId}/files", consumes = "multipart/form-data")
    ResponseEntity<Uploaded> addFile(@AuthenticationPrincipal ApplicantPrincipal me, @PathVariable UUID id,
                                     @PathVariable UUID docId, @RequestPart("file") MultipartFile file,
                                     @RequestParam(value = "mode", defaultValue = "add") String mode,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String key);

    /** Удалить документ. */
    @DeleteMapping("/applications/{id}/documents/{docId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal ApplicantPrincipal me, @PathVariable UUID id,
                                @PathVariable UUID docId);

    /** Отправить комплект. */
    @PostMapping("/applications/{id}/submit")
    SubmitRs submit(@AuthenticationPrincipal ApplicantPrincipal me, @PathVariable UUID id);

    /** Билет SSE. */
    @GetMapping("/applications/{id}/stream/ticket")
    TicketRs ticket(@AuthenticationPrincipal ApplicantPrincipal me, @PathVariable UUID id);

    /** Поток стадий (SSE по билету). */
    @GetMapping(value = "/applications/{id}/stream", produces = "text/event-stream")
    SseEmitter stream(@PathVariable UUID id, @RequestParam("ticket") String ticket);
}
