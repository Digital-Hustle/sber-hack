package ru.documents.intake.api.controller.impl;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import ru.documents.intake.api.controller.InternalController;
import ru.documents.intake.api.dto.response.ActiveFilesRs;
import ru.documents.intake.features.applications.command.service.FileContentService;
import ru.documents.intake.features.applications.model.ApplicationView;
import ru.documents.intake.features.applications.model.FileContent;
import ru.documents.intake.features.applications.query.service.ApplicationQueryService;

/**
 * Внутренние ручки для docproc и review.
 */
@RestController
public class InternalControllerImpl implements InternalController {

    private final FileContentService files;
    private final ApplicationQueryService queries;

    public InternalControllerImpl(FileContentService files, ApplicationQueryService queries) {
        this.files = files;
        this.queries = queries;
    }

    @Override
    public ResponseEntity<byte[]> file(Authentication auth, UUID fileId) {
        FileContent c = files.original(fileId, auth.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(c.mime())).body(c.bytes());
    }

    @Override
    public ActiveFilesRs files(UUID docId) {
        var files = queries.activeFiles(docId);
        return new ActiveFilesRs(files.files().stream()
                .map(f -> new ActiveFilesRs.Item(f.fileId(), f.mime(), f.pages())).toList(), files.onlyPages());
    }

    @Override
    public ApplicationView summary(UUID id) {
        return queries.view(id);
    }
}
