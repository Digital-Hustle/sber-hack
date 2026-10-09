package ru.documents.docproc.features.processing.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

import ru.documents.docproc.features.processing.model.FieldResult;
import ru.documents.docproc.features.processing.model.FinalResult;
import ru.documents.docproc.features.processing.model.Finding;
import ru.documents.docproc.features.processing.model.Stages;

/**
 * Сборка итогов из выходов стадий (одна форма для всех исходов).
 */
@Component
public class Results {

    private final ObjectMapper json;

    public Results(ObjectMapper json) {
        this.json = json;
    }

    /** Нечитаемо (до модели). */
    public FinalResult unreadable(JsonNode text, String code) {
        return build(Stages.UNREADABLE, Stages.UNKNOWN, null, List.of(), List.of(Finding.of(code)), List.of(), text, null,
                null, null, Map.of(Stages.EXTRACTING_TEXT, text));
    }

    /** Итог с указанными полями. */
    public FinalResult build(String stage, String type, Double typeConf, List<String> evidence, List<Finding> findings,
                             List<FieldResult> fields, JsonNode text, String model, String prompt, Integer schemaVersion,
                             Map<String, JsonNode> outputs) {
        Map<String, Long> durations = new LinkedHashMap<>();
        for (String s : List.of(Stages.EXTRACTING_TEXT, Stages.CLASSIFYING, Stages.EXTRACTING_FIELDS, Stages.VALIDATING)) {
            JsonNode o = outputs.get(s);
            if (o != null && o.has("ms")) {
                durations.put(s, o.path("ms").asLong());
            }
        }
        ArrayNode quality = json.createArrayNode();
        ArrayNode pages = json.createArrayNode();
        JsonNode mrz = null;
        if (text != null) {
            for (JsonNode p : text.path("pages")) {
                pages.addObject().put("n", p.path("n").asInt()).put("width", p.path("width").asInt())
                        .put("height", p.path("height").asInt()).put("textLayer", p.path("textLayer").asBoolean())
                        .put("meanConf", p.path("meanConf").asDouble()).put("fileId", p.path("fileId").asText())
                        .put("filePage", p.path("filePage").asInt(1)).put("rotation", p.path("rotation").asInt(0));
                if (p.hasNonNull("quality")) {
                    quality.add(json.createObjectNode().put("n", p.path("n").asInt()).setAll((com.fasterxml.jackson.databind
                            .node.ObjectNode) p.get("quality")));
                }
            }
            mrz = text.get("mrz");
        }
        List<Finding> all = new ArrayList<>(findings);
        return new FinalResult(stage, type, typeConf, evidence, all, fields, quality, mrz, pages, model, prompt,
                schemaVersion, durations);
    }
}
