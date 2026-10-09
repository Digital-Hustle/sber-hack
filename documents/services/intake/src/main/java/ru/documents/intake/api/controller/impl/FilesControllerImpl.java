package ru.documents.intake.api.controller.impl;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import ru.documents.common.exception.DomainException;
import ru.documents.common.security.KeycloakJwt;
import ru.documents.intake.api.controller.FilesController;
import ru.documents.intake.features.applications.command.service.FileContentService;
import ru.documents.intake.features.applications.model.ApplicationView;
import ru.documents.intake.features.applications.model.FileContent;
import ru.documents.intake.features.applications.query.service.ApplicationQueryService;
import ru.documents.intake.security.ApplicantPrincipal;

/**
 * Выдача файлов: оператор — по JWT с ролью, соискатель — только файлы своей заявки.
 */
@RestController
public class FilesControllerImpl implements FilesController {

    private final FileContentService files;
    private final ApplicationQueryService queries;

    public FilesControllerImpl(FileContentService files, ApplicationQueryService queries) {
        this.files = files;
        this.queries = queries;
    }

    @Override
    public ResponseEntity<byte[]> content(Authentication auth, UUID fileId, Integer page) {
        String actor;
        String kind;
        if (auth.getPrincipal() instanceof ApplicantPrincipal me) {
            if (!files.owner(fileId).equals(me.applicationId())) {
                throw DomainException.notFound();
            }
            actor = me.applicationId().toString();
            kind = "APPLICANT";
        } else if (auth instanceof JwtAuthenticationToken jwt) {
            actor = KeycloakJwt.username(jwt.getToken());
            kind = "OPERATOR";
        } else {
            throw DomainException.notFound();
        }
        FileContent c = files.page(fileId, page, actor, kind);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Total-Pages", String.valueOf(c.pages()))
                .header("Access-Control-Expose-Headers", "X-Total-Pages")
                .contentType(MediaType.parseMediaType(c.mime())).body(c.bytes());
    }

    @Override
    public ApplicationView operatorApplication(UUID id) {
        return queries.view(id);
    }

    @Override
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
