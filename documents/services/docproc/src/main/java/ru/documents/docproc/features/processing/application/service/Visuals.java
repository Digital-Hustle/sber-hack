package ru.documents.docproc.features.processing.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import ru.documents.docproc.features.processing.gateway.IntakeGateway;
import ru.documents.docproc.features.processing.gateway.OcrGateway;
import ru.documents.docproc.features.processing.model.Job;

/**
 * Картинки страниц для vision-пути (FR-29): первые страницы документа через ocr /v1/render.
 */
@Component
public class Visuals {

    private static final int MAX_PAGES = 2;

    private final IntakeGateway intake;
    private final OcrGateway ocr;

    public Visuals(IntakeGateway intake, OcrGateway ocr) {
        this.intake = intake;
        this.ocr = ocr;
    }

    /** PNG первых страниц. */
    public List<byte[]> pages(Job job, JsonNode text) {
        List<byte[]> out = new ArrayList<>();
        Map<String, byte[]> files = new HashMap<>();
        for (JsonNode p : text.path("pages")) {
            if (out.size() >= MAX_PAGES) {
                break;
            }
            String fileId = p.path("fileId").asText();
            byte[] bytes = files.computeIfAbsent(fileId, id -> intake.content(UUID.fromString(id)));
            out.add(ocr.render(bytes, p.path("filePage").asInt(1)));
        }
        return out;
    }
}
