package ru.documents.intake.api.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ru.documents.intake.features.applications.model.ApplicationView;

/**
 * Файлы и документы для оператора; соискателю — только свои файлы.
 */
@Tag(name = "files")
@RequestMapping("/api/v1")
public interface FilesController {

    /** Страница файла (no-store, аудит). */
    @GetMapping("/files/{fileId}/content")
    ResponseEntity<byte[]> content(Authentication auth, @PathVariable UUID fileId,
                                   @RequestParam(value = "page", required = false) Integer page);

    /** Заявка для оператора (документы, версии файлов). */
    @GetMapping("/operator/applications/{id}")
    ApplicationView operatorApplication(@PathVariable UUID id);

    /** Проверка доступа оператора. */
    @GetMapping("/operator/ping")
    java.util.Map<String, String> ping();
}
