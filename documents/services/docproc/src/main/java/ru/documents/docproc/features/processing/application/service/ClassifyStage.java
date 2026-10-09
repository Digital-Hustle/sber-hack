package ru.documents.docproc.features.processing.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import ru.documents.docproc.config.DocprocProperties;
import ru.documents.docproc.features.processing.model.DocType;
import ru.documents.docproc.features.processing.model.Finding;
import ru.documents.docproc.features.processing.model.Job;
import ru.documents.docproc.features.processing.model.Stages;
import ru.documents.docproc.features.processing.model.StepOutcome;
import ru.documents.docproc.features.processing.query.service.TypeRegistry;
import ru.documents.docproc.features.processing.rules.Text;
import ru.documents.llm.api.LlmGateway;
import ru.documents.llm.api.LlmRequest;
import ru.documents.llm.api.LlmResult;

/**
 * CLASSIFYING: вид документа по содержимому через модель (FR-22); имя файла и пункт в промпт не идут.
 */
@Component
public class ClassifyStage {

    private static final Pattern SNILS = Pattern.compile("\\d{3}[- ]\\d{3}[- ]\\d{3}[ -]\\d{2}");
    private static final Pattern INN12 = Pattern.compile("(?<!\\d)\\d{12}(?!\\d)");
    private static final Pattern DEPT = Pattern.compile("(?<!\\d)\\d{3}-\\d{3}(?!\\d)");
    private static final Pattern RF_SERIES = Pattern.compile("(?<!\\d)\\d{2}\\s?\\d{2}\\s?\\d{6}(?!\\d)");
    private static final double EVIDENCE_PENALTY = 0.6;

    private final LlmGateway llm;
    private final TypeRegistry types;
    private final Prompts prompts;
    private final DocprocProperties props;
    private final ObjectMapper json;
    private final Visuals visuals;
    private final Results results;

    public ClassifyStage(LlmGateway llm, TypeRegistry types, Prompts prompts, DocprocProperties props, ObjectMapper json,
                         Visuals visuals, Results results) {
        this.llm = llm;
        this.types = types;
        this.prompts = prompts;
        this.props = props;
        this.json = json;
        this.visuals = visuals;
        this.results = results;
    }

    /** Выполнить. */
    public StepOutcome run(Job job, Map<String, JsonNode> outputs) {
        JsonNode text = outputs.get(Stages.EXTRACTING_TEXT);
        ObjectNode out = json.createObjectNode();
        List<DocType> registry = types.all();
        if (job.forcedType() != null && registry.stream().anyMatch(t -> t.code().equals(job.forcedType()))) {
            out.put("type", job.forcedType()).put("confidence", 1.0).put("forced", true);
            out.putArray("evidence");
            return StepOutcome.next(out);
        }
        String full = fullText(text);
        StringBuilder list = new StringBuilder();
        for (DocType t : registry) {
            list.append("- ").append(t.code()).append(" — ").append(t.name()).append(". ").append(t.signs()).append('\n');
        }
        String system = prompts.render(Prompts.CLASSIFY, Map.of("types", list.toString().trim()));
        String user = prompts.render(Prompts.CLASSIFY + ".user", Map.of("flags", flags(text, full),
                "text", cut(full, props.classifyTextChars())));
        List<String> codes = new ArrayList<>(registry.stream().map(DocType::code).toList());
        codes.add(Stages.UNKNOWN);
        LlmResult r = llm.structured(new LlmRequest(Stages.CLASSIFYING, job.documentId().toString(), system, user,
                schema(codes), job.vision() ? visuals.pages(job, text) : List.of()));
        String type = r.json().path("type").asText(Stages.UNKNOWN);
        double conf = r.json().path("confidence").asDouble(0);
        ArrayNode evidence = out.putArray("evidence");
        int found = 0;
        int total = 0;
        String key = Text.key(full);
        for (JsonNode e : r.json().path("evidence")) {
            String s = e.asText();
            if (s.isBlank()) {
                continue;
            }
            total++;
            evidence.add(s);
            if (key.contains(Text.key(s))) {
                found++;
            }
        }
        if (!job.vision() && total > 0) {
            conf = found == 0 ? conf * EVIDENCE_PENALTY : conf * (0.8 + 0.2 * found / total);
        }
        if (!codes.contains(type)) {
            type = Stages.UNKNOWN;
        }
        conf = Math.round(conf * 1000) / 1000.0;
        out.put("type", type).put("confidence", conf).put("model", r.model()).put("promptVersion", Prompts.CLASSIFY)
                .put("evidenceFound", found);
        if (Stages.UNKNOWN.equals(type) || conf < props.classifyMinConfidence()) {
            out.put("ms", 0);
            Map<String, JsonNode> all = new java.util.HashMap<>(outputs);
            all.put(Stages.CLASSIFYING, out);
            List<String> ev = new ArrayList<>();
            evidence.forEach(n -> ev.add(n.asText()));
            return new StepOutcome(out, results.build(Stages.NEEDS_REVIEW, Stages.UNKNOWN, conf, ev,
                    List.of(new Finding("TYPE_UNKNOWN", Map.of("suggested", type))), List.of(), text, r.model(),
                    Prompts.CLASSIFY, null, all));
        }
        return StepOutcome.next(out);
    }

    static String fullText(JsonNode text) {
        StringBuilder b = new StringBuilder();
        for (JsonNode p : text.path("pages")) {
            b.append(p.path("text").asText("")).append('\n');
        }
        return b.toString();
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String flags(JsonNode text, String full) {
        List<String> f = new ArrayList<>();
        JsonNode mrz = text.path("mrz");
        if (mrz.isObject()) {
            f.add("- найдена машиночитаемая зона (MRZ) формата " + mrz.path("format").asText() + ", код документа «"
                    + mrz.path("fields").path("documentCode").asText() + "», государство «"
                    + mrz.path("fields").path("issuingState").asText() + "», контрольные цифры "
                    + (mrz.path("valid").asBoolean() ? "сходятся" : "не сходятся"));
        } else {
            f.add("- машиночитаемая зона (MRZ) не найдена");
        }
        if (SNILS.matcher(full).find()) {
            f.add("- есть номер вида NNN-NNN-NNN NN (как у СНИЛС)");
        }
        if (INN12.matcher(full).find()) {
            f.add("- есть число из 12 цифр (как ИНН физлица)");
        }
        if (DEPT.matcher(full).find()) {
            f.add("- есть код вида NNN-NNN (как код подразделения)");
        }
        if (RF_SERIES.matcher(full).find()) {
            f.add("- есть номер вида NN NN NNNNNN (как серия и номер паспорта РФ)");
        }
        f.add("- страниц: " + text.path("pages").size());
        return String.join("\n", f);
    }

    private JsonNode schema(List<String> codes) {
        ObjectNode s = json.createObjectNode().put("type", "object");
        ObjectNode p = s.putObject("properties");
        ArrayNode en = p.putObject("type").put("type", "string").putArray("enum");
        codes.forEach(en::add);
        p.putObject("confidence").put("type", "number").put("minimum", 0).put("maximum", 1);
        p.putObject("evidence").put("type", "array").put("maxItems", 5).putObject("items").put("type", "string");
        s.putArray("required").add("type").add("confidence").add("evidence");
        s.put("additionalProperties", false);
        return s;
    }
}
