package ru.documents.docproc.features.processing.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import ru.documents.docproc.config.DocprocProperties;
import ru.documents.docproc.features.processing.model.DocType;
import ru.documents.docproc.features.processing.model.Job;
import ru.documents.docproc.features.processing.model.Stages;
import ru.documents.docproc.features.processing.model.StepOutcome;
import ru.documents.docproc.features.processing.query.service.TypeRegistry;
import ru.documents.llm.api.LlmGateway;
import ru.documents.llm.api.LlmRequest;
import ru.documents.llm.api.LlmResult;

/**
 * EXTRACTING_FIELDS: поля по JSON Schema вида через модель в режиме structured output (FR-23).
 */
@Component
public class ExtractStage {

    private final LlmGateway llm;
    private final TypeRegistry types;
    private final Prompts prompts;
    private final DocprocProperties props;
    private final ObjectMapper json;
    private final Visuals visuals;

    public ExtractStage(LlmGateway llm, TypeRegistry types, Prompts prompts, DocprocProperties props, ObjectMapper json,
                        Visuals visuals) {
        this.llm = llm;
        this.types = types;
        this.prompts = prompts;
        this.props = props;
        this.json = json;
        this.visuals = visuals;
    }

    /** Выполнить. */
    public StepOutcome run(Job job, Map<String, JsonNode> outputs) {
        JsonNode cls = outputs.get(Stages.CLASSIFYING);
        JsonNode text = onlyPages(outputs.get(Stages.EXTRACTING_TEXT), cls.path("pages"));
        String code = cls.path("type").asText();
        DocType type = types.get(code).orElseThrow(() -> new IllegalStateException("type " + code));
        StringBuilder fields = new StringBuilder();
        type.schema().path("properties").properties().forEach(e -> fields.append("- ").append(e.getKey()).append(" (")
                .append(e.getValue().path("title").asText()).append("): ").append(e.getValue().path("description").asText())
                .append('\n'));
        StringBuilder body = new StringBuilder();
        for (JsonNode p : text.path("pages")) {
            body.append("=== Страница ").append(p.path("n").asInt()).append(" ===\n").append(p.path("text").asText(""))
                    .append('\n');
        }
        String mrz = "";
        JsonNode m = text.path("mrz");
        if (m.isObject()) {
            StringBuilder lines = new StringBuilder("Машиночитаемая зона (MRZ):\n");
            m.path("raw").forEach(l -> lines.append(l.asText()).append('\n'));
            mrz = lines.append('\n').toString();
        }
        String system = prompts.render(Prompts.EXTRACT, Map.of("typeName", type.name(), "fields", fields.toString().trim()));
        String user = prompts.render(Prompts.EXTRACT + ".user", Map.of("mrz", mrz,
                "text", ClassifyStage.cut(body.toString(), props.extractTextChars())));
        List<byte[]> images = job.vision() ? visuals.pages(job, text) : List.of();
        LlmResult r = llm.structured(new LlmRequest(Stages.EXTRACTING_FIELDS, job.documentId().toString(), system, user,
                schema(type), images));
        ObjectNode out = json.createObjectNode();
        out.set("fields", r.json());
        out.put("type", type.code()).put("schemaVersion", type.version()).put("model", r.model())
                .put("promptVersion", Prompts.EXTRACT);
        return StepOutcome.next(out);
    }

    /**
     * Только страницы основного документа, если классификация разметила файл (DP-14): поля другого документа
     * из того же файла не должны попасть в этот.
     */
    JsonNode onlyPages(JsonNode text, JsonNode pages) {
        if (!pages.isArray() || pages.isEmpty() || pages.size() >= text.path("pages").size()) {
            return text;
        }
        Set<Integer> keep = new HashSet<>();
        pages.forEach(n -> keep.add(n.asInt()));
        ObjectNode copy = text.deepCopy();
        ArrayNode filtered = json.createArrayNode();
        text.path("pages").forEach(p -> {
            if (keep.contains(p.path("n").asInt())) {
                filtered.add(p);
            }
        });
        copy.set("pages", filtered);
        return copy;
    }

    /** Схема ответа модели по схеме вида: по каждому полю value/raw/page/confidence/reason. */
    JsonNode schema(DocType type) {
        ObjectNode s = json.createObjectNode().put("type", "object");
        ObjectNode props = s.putObject("properties");
        ArrayNode req = s.putArray("required");
        type.schema().path("properties").properties().forEach(e -> {
            ObjectNode f = props.putObject(e.getKey()).put("type", "object");
            ObjectNode fp = f.putObject("properties");
            fp.putObject("value").putArray("type").add("string").add("null");
            fp.putObject("raw").putArray("type").add("string").add("null");
            fp.putObject("page").putArray("type").add("integer").add("null");
            fp.putObject("confidence").put("type", "number").put("minimum", 0).put("maximum", 1);
            fp.putObject("reason").putArray("type").add("string").add("null");
            f.putArray("required").add("value").add("raw").add("page").add("confidence").add("reason");
            f.put("additionalProperties", false);
            req.add(e.getKey());
        });
        s.put("additionalProperties", false);
        return s;
    }
}
