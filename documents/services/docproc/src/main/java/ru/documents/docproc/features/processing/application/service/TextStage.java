package ru.documents.docproc.features.processing.application.service;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import ru.documents.docproc.config.DocprocProperties;
import ru.documents.docproc.features.processing.gateway.IntakeGateway;
import ru.documents.docproc.features.processing.gateway.OcrGateway;
import ru.documents.docproc.features.processing.model.FileRef;
import ru.documents.docproc.features.processing.model.Finding;
import ru.documents.docproc.features.processing.model.Job;
import ru.documents.docproc.features.processing.model.StepOutcome;
import ru.documents.llm.api.LlmGateway;

/**
 * EXTRACTING_TEXT: файлы из intake → ocr; решение «нечитаемо» по метрикам качества (FR-21, FR-25).
 */
@Component
public class TextStage {

    private final IntakeGateway intake;
    private final OcrGateway ocr;
    private final LlmGateway llm;
    private final DocprocProperties props;
    private final ObjectMapper json;
    private final Results results;

    public TextStage(IntakeGateway intake, OcrGateway ocr, LlmGateway llm, DocprocProperties props, ObjectMapper json,
                     Results results) {
        this.intake = intake;
        this.ocr = ocr;
        this.llm = llm;
        this.props = props;
        this.json = json;
        this.results = results;
    }

    /** Выполнить. */
    public StepOutcome run(Job job) {
        List<FileRef> files = intake.activeFiles(job.documentId());
        ObjectNode out = json.createObjectNode();
        ArrayNode pages = out.putArray("pages");
        ArrayNode fileList = out.putArray("files");
        JsonNode mrz = null;
        int offset = 0;
        for (FileRef f : files) {
            JsonNode r = ocr.extract(intake.content(f.fileId()));
            int filePages = 0;
            for (JsonNode p : r.path("pages")) {
                ObjectNode page = ((ObjectNode) p).deepCopy();
                page.put("filePage", p.path("n").asInt());
                page.put("n", offset + p.path("n").asInt());
                page.put("fileId", f.fileId().toString());
                pages.add(page);
                filePages++;
            }
            offset += filePages;
            fileList.addObject().put("fileId", f.fileId().toString()).put("pages", filePages);
            JsonNode m = r.path("mrz");
            if (!m.isMissingNode() && !m.isNull() && (mrz == null || m.path("valid").asBoolean())) {
                mrz = m;
            }
        }
        if (mrz != null) {
            out.set("mrz", mrz);
        }
        int chars = 0;
        double confSum = 0;
        int words = 0;
        for (JsonNode p : pages) {
            for (char c : p.path("text").asText("").toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    chars++;
                }
            }
            int n = p.path("words").size();
            confSum += p.path("meanConf").asDouble() * n;
            words += n;
        }
        double meanConf = words == 0 ? 0 : confSum / words;
        out.put("textChars", chars);
        out.put("meanConf", Math.round(meanConf * 1000) / 1000.0);
        String quality = qualityReason(pages);
        boolean weak = files.isEmpty() || chars < props.minTextChars() || meanConf * 100 < props.ocrMinConfidence();
        boolean visionAvailable = props.visionFallbackEnabled() && llm.status().visionModel() != null
                && !llm.status().visionModel().isBlank();
        if (weak && visionAvailable && !files.isEmpty()) {
            out.put("vision", true);
            return StepOutcome.next(out);
        }
        if (files.isEmpty() || chars < props.minTextChars()) {
            String code = quality != null ? quality : "TEXT_NOT_FOUND";
            return new StepOutcome(out, results.unreadable(out, code));
        }
        if (meanConf * 100 < props.ocrMinConfidence() && quality != null) {
            return new StepOutcome(out, results.unreadable(out, quality));
        }
        ArrayNode findings = out.putArray("findings");
        if (pages.findValues("cutOff").stream().anyMatch(JsonNode::asBoolean)) {
            findings.add(json.valueToTree(Finding.of("IMAGE_CUT_OFF")));
        }
        return StepOutcome.next(out);
    }

    private String qualityReason(ArrayNode pages) {
        List<String> reasons = new ArrayList<>();
        for (JsonNode p : pages) {
            JsonNode q = p.path("quality");
            if (q.isMissingNode() || q.isNull() || p.path("textLayer").asBoolean()) {
                continue;
            }
            if (q.path("brightness").asDouble(1) < props.darkMax()) {
                reasons.add("IMAGE_TOO_DARK");
            } else if (q.path("glare").asDouble(0) > props.glareMax()) {
                reasons.add("IMAGE_GLARE");
            } else if (q.path("blur").asDouble(Double.MAX_VALUE) < props.blurMin()) {
                reasons.add("IMAGE_BLURRY");
            } else if (q.path("cutOff").asBoolean(false)) {
                reasons.add("IMAGE_CUT_OFF");
            }
        }
        for (String r : List.of("IMAGE_TOO_DARK", "IMAGE_GLARE", "IMAGE_BLURRY", "IMAGE_CUT_OFF")) {
            if (reasons.contains(r)) {
                return r;
            }
        }
        return null;
    }
}
