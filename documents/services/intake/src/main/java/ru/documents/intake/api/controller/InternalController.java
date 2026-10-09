package ru.documents.intake.api.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import ru.documents.intake.api.dto.response.ActiveFilesRs;
import ru.documents.intake.features.applications.model.ApplicationView;

/**
 * Внутренние ручки (service accounts, наружу gateway не пускает).
 */
@Hidden
@RequestMapping("/internal/intake")
public interface InternalController {

    /** Расшифрованный файл (svc-docproc). */
    @GetMapping("/files/{fileId}")
    ResponseEntity<byte[]> file(Authentication auth, @PathVariable UUID fileId);

    /** Активные файлы документа. */
    @GetMapping("/documents/{docId}/files")
    ActiveFilesRs files(@PathVariable UUID docId);

    /** Сводка заявки (svc-review). */
    @GetMapping("/applications/{id}/summary")
    ApplicationView summary(@PathVariable UUID id);
}
