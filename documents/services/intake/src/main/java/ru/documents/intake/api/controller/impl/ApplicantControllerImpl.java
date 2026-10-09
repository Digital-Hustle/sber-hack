package ru.documents.intake.api.controller.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ru.documents.common.exception.DomainException;
import ru.documents.intake.api.controller.ApplicantController;
import ru.documents.intake.api.dto.request.CreateApplicationRq;
import ru.documents.intake.api.dto.request.ProfileRq;
import ru.documents.intake.api.dto.request.ResumeRq;
import ru.documents.intake.api.dto.response.RequiredDocumentsRs;
import ru.documents.intake.api.dto.response.SubmitRs;
import ru.documents.intake.api.dto.response.TicketRs;
import ru.documents.intake.features.applications.command.service.ApplicationCommandService;
import ru.documents.intake.features.applications.command.service.UploadService;
import ru.documents.intake.features.applications.model.ApplicationView;
import ru.documents.intake.features.applications.model.Created;
import ru.documents.intake.features.applications.model.Uploaded;
import ru.documents.intake.features.applications.query.service.ApplicationQueryService;
import ru.documents.intake.features.applications.rules.Questionnaire;
import ru.documents.intake.features.applications.rules.Questionnaire.Question;
import ru.documents.intake.features.stream.api.StreamHub;
import ru.documents.intake.security.ApplicantPrincipal;

/**
 * Реализация кабинета соискателя. Ключ заявки открывает только свою заявку: чужой id → 404 (IDOR).
 */
@RestController
public class ApplicantControllerImpl implements ApplicantController {

    private final ApplicationCommandService commands;
    private final ApplicationQueryService queries;
    private final UploadService uploads;
    private final Questionnaire questionnaire;
    private final StreamHub hub;

    public ApplicantControllerImpl(ApplicationCommandService commands, ApplicationQueryService queries,
                                   UploadService uploads, Questionnaire questionnaire, StreamHub hub) {
        this.commands = commands;
        this.queries = queries;
        this.uploads = uploads;
        this.questionnaire = questionnaire;
        this.hub = hub;
    }

    @Override
    public Map<String, List<Question>> questionnaire() {
        return Map.of("questions", questionnaire.questions());
    }

    @Override
    public ResponseEntity<Created> create(CreateApplicationRq rq) {
        CreateApplicationRq.Contact c = rq.contact();
        Created created = commands.create(rq.consentPd(), c == null ? null : c.phone(), c == null ? null : c.email(),
                Boolean.TRUE.equals(rq.demo()));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @Override
    public Created resume(ResumeRq rq) {
        return commands.resume(rq.phoneOrEmail(), rq.resumeCode());
    }

    @Override
    public ApplicationView get(ApplicantPrincipal me, UUID id) {
        return queries.view(own(me, id));
    }

    @Override
    public RequiredDocumentsRs profile(ApplicantPrincipal me, UUID id, ProfileRq rq) {
        return new RequiredDocumentsRs(commands.saveProfile(own(me, id), rq.answers()).stream()
                .map(r -> new RequiredDocumentsRs.Item(r.type(), r.title(), r.hint(), r.required())).toList());
    }

    @Override
    public ResponseEntity<Uploaded> upload(ApplicantPrincipal me, UUID id, MultipartFile file, String slotType,
                                           String key) {
        return ResponseEntity.accepted().body(uploads.upload(own(me, id), bytes(file), slotType, key));
    }

    @Override
    public ResponseEntity<Uploaded> addFile(ApplicantPrincipal me, UUID id, UUID docId, MultipartFile file,
                                            String mode, String key) {
        return ResponseEntity.accepted().body(uploads.addFile(own(me, id), docId, bytes(file),
                "replace".equals(mode), key));
    }

    @Override
    public ResponseEntity<Void> delete(ApplicantPrincipal me, UUID id, UUID docId) {
        uploads.delete(own(me, id), docId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteApplication(ApplicantPrincipal me, UUID id) {
        commands.delete(own(me, id));
        return ResponseEntity.noContent().build();
    }

    @Override
    public SubmitRs submit(ApplicantPrincipal me, UUID id) {
        return new SubmitRs("SUBMITTED", commands.submit(own(me, id)));
    }

    @Override
    public TicketRs ticket(ApplicantPrincipal me, UUID id) {
        return new TicketRs(commands.ticket(own(me, id)));
    }

    @Override
    public SseEmitter stream(UUID id, String ticket) {
        commands.consumeTicket(ticket, id);
        return hub.subscribe(id);
    }

    private static UUID own(ApplicantPrincipal me, UUID id) {
        if (me == null || !me.applicationId().equals(id)) {
            throw DomainException.notFound();
        }
        return id;
    }

    private static byte[] bytes(MultipartFile f) {
        try {
            return f == null ? new byte[0] : f.getBytes();
        } catch (IOException e) {
            throw new DomainException("FILE_CORRUPTED", 422, "Файл не дочитан", "Загрузите ещё раз");
        }
    }
}
