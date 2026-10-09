package ru.documents.docproc.features.processing.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import ru.documents.docproc.config.DocprocProperties;
import ru.documents.docproc.features.processing.model.DocType;
import ru.documents.docproc.features.processing.model.FieldResult;
import ru.documents.docproc.features.processing.model.FieldResult.Check;
import ru.documents.docproc.features.processing.model.FinalResult;
import ru.documents.docproc.features.processing.model.Finding;
import ru.documents.docproc.features.processing.model.Stages;
import ru.documents.docproc.features.processing.model.StepOutcome;
import ru.documents.docproc.features.processing.query.service.TypeRegistry;
import ru.documents.docproc.features.processing.rules.ConfidenceScorer;
import ru.documents.docproc.features.processing.rules.Dates;
import ru.documents.docproc.features.processing.rules.FieldRules;
import ru.documents.docproc.features.processing.rules.Text;
import ru.documents.docproc.features.processing.rules.WordLocator;

/**
 * VALIDATING: нормализация, проверки (даты, контрольные суммы, MRZ), источник (bbox), уверенность (FR-24, B-02).
 */
@Component
public class ValidateStage {

    private static final double NOT_LOCATED = 0.25;
    private static final int ADULT_AT_ISSUE = 14;

    private final TypeRegistry types;
    private final DocprocProperties props;
    private final Clock clock;
    private final ObjectMapper json;
    private final Results results;
    private final ConfidenceScorer scorer;

    public ValidateStage(TypeRegistry types, DocprocProperties props, Clock clock, ObjectMapper json, Results results) {
        this.types = types;
        this.props = props;
        this.clock = clock;
        this.json = json;
        this.results = results;
        this.scorer = new ConfidenceScorer(props.weightModel(), props.weightOcr(), props.weightChecks(),
                props.checksNotApplicable());
    }

    /** Выполнить. */
    public StepOutcome run(Map<String, JsonNode> outputs) {
        JsonNode text = outputs.get(Stages.EXTRACTING_TEXT);
        JsonNode cls = outputs.get(Stages.CLASSIFYING);
        JsonNode ext = outputs.get(Stages.EXTRACTING_FIELDS);
        DocType type = types.get(ext.path("type").asText()).orElseThrow();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(props.zone())));
        List<Finding> findings = new ArrayList<>();
        text.path("findings").forEach(f -> findings.add(json.convertValue(f, Finding.class)));
        Map<String, FieldRules.Normalized> norm = new HashMap<>();
        List<FieldResult> fields = new ArrayList<>();
        JsonNode model = ext.path("fields");
        type.schema().path("properties").properties().forEach(e -> {
            JsonNode m = model.path(e.getKey());
            norm.put(e.getKey(), FieldRules.apply(e.getValue().path("x-check").asText(), str(m, "value"), today));
        });
        crossChecks(type.code(), norm, text.path("mrz"), today, findings);
        boolean review = !findings.isEmpty();
        for (var e : type.schema().path("properties").properties()) {
            String name = e.getKey();
            JsonNode def = e.getValue();
            JsonNode m = model.path(name);
            FieldRules.Normalized n = norm.get(name);
            String raw = str(m, "raw");
            Integer page = m.path("page").canConvertToInt() ? m.path("page").asInt() : null;
            String reason = str(m, "reason");
            boolean required = def.path("x-required").asBoolean(false);
            if (n.value() == null) {
                fields.add(new FieldResult(name, def.path("title").asText(name), null, raw, page, null, null, n.checks(),
                        reason == null ? "NOT_FOUND" : reason));
                review |= required;
                continue;
            }
            Optional<WordLocator.Location> loc = WordLocator.find(text.path("pages"), raw != null ? raw : n.value(), page);
            if (loc.isEmpty() && raw != null) {
                loc = WordLocator.find(text.path("pages"), str(m, "value"), page);
            }
            double ocr = loc.map(WordLocator.Location::ocrConf).orElse(NOT_LOCATED);
            double conf = scorer.score(m.path("confidence").asDouble(0.5), ocr, n.checks());
            boolean failed = n.checks().stream().anyMatch(c -> !c.ok());
            fields.add(new FieldResult(name, def.path("title").asText(name), n.value(), raw,
                    loc.map(WordLocator.Location::page).orElse(page), loc.map(WordLocator.Location::bbox).orElse(null),
                    conf, n.checks(), null));
            review |= failed || conf < props.fieldReviewThreshold();
            if (failed) {
                addFieldFindings(def, n, findings);
            }
        }
        List<String> evidence = new ArrayList<>();
        cls.path("evidence").forEach(x -> evidence.add(x.asText()));
        FinalResult r = results.build(review ? Stages.NEEDS_REVIEW : Stages.PARSED, type.code(),
                cls.path("confidence").asDouble(), evidence, dedupe(findings), fields, text, ext.path("model").asText(null),
                cls.path("promptVersion").asText(Prompts.CLASSIFY) + "+" + ext.path("promptVersion").asText(), type.version(),
                outputs);
        ObjectNode out = json.createObjectNode().put("stage", r.stage());
        return new StepOutcome(out, r);
    }

    private static void addFieldFindings(JsonNode def, FieldRules.Normalized n, List<Finding> findings) {
        String title = def.path("title").asText();
        for (Check c : n.checks()) {
            if (c.ok()) {
                continue;
            }
            switch (c.code()) {
                case "NOT_EXPIRED" -> findings.add(new Finding("DOCUMENT_EXPIRED", Map.of("date", n.value())));
                case "SNILS_CHECKSUM", "INN_CHECKSUM", "MRZ_CHECKSUM" -> findings.add(new Finding("CHECKSUM_FAILED",
                        Map.of("field", title)));
                case "MRZ_MATCH" -> findings.add(new Finding("MRZ_MISMATCH", Map.of("field", title)));
                default -> {
                    // Остальные проверки не дают отдельных находок.
                }
            }
        }
    }

    private static List<Finding> dedupe(List<Finding> in) {
        List<Finding> out = new ArrayList<>();
        for (Finding f : in) {
            if (!out.contains(f)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Проверки между полями и с MRZ. */
    void crossChecks(String type, Map<String, FieldRules.Normalized> n, JsonNode mrz, LocalDate today,
                     List<Finding> findings) {
        LocalDate birth = date(n, "birthDate");
        LocalDate issue = date(n, "issueDate");
        if (birth != null && issue != null) {
            addCheck(n, "issueDate", new Check("AFTER_BIRTH", issue.isAfter(birth)));
            if ("PASSPORT_RF".equals(type)) {
                addCheck(n, "issueDate", new Check("AGE_AT_ISSUE", !issue.isBefore(birth.plusYears(ADULT_AT_ISSUE))));
            }
        }
        LocalDate expiry = date(n, "expiryDate");
        if (issue != null && expiry != null) {
            addCheck(n, "expiryDate", new Check("AFTER_ISSUE", expiry.isAfter(issue)));
        }
        if (!"PASSPORT_FOREIGN".equals(type) || !mrz.isObject()) {
            return;
        }
        JsonNode mf = mrz.path("fields");
        boolean numberOk = true;
        for (JsonNode c : mrz.path("checks")) {
            if (!c.path("ok").asBoolean()) {
                numberOk &= !"documentNumber".equals(c.path("name").asText());
                findings.add(new Finding("CHECKSUM_FAILED", Map.of("field", "MRZ")));
                break;
            }
        }
        addCheck(n, "documentNumber", new Check("MRZ_CHECKSUM", numberOk));
        compare(n, "documentNumber", Text.key(mf.path("documentNumber").asText()), false);
        compare(n, "surname", Text.key(mf.path("surname").asText()), true);
        compare(n, "givenNames", Text.key(mf.path("givenNames").asText()), true);
        Dates.mrz(mf.path("birthDate").asText(), today, false).ifPresent(d -> compareDate(n, "birthDate", d));
        Dates.mrz(mf.path("expiryDate").asText(), today, true).ifPresent(d -> compareDate(n, "expiryDate", d));
    }

    private static void compare(Map<String, FieldRules.Normalized> n, String field, String mrzValue, boolean name) {
        FieldRules.Normalized f = n.get(field);
        if (f == null || f.value() == null || mrzValue.isEmpty()) {
            return;
        }
        String v = name ? Text.latin(f.value()) : Text.key(f.value());
        boolean ok = name ? levenshtein(v, mrzValue) <= 1 || v.startsWith(mrzValue) || mrzValue.startsWith(v)
                : v.equals(mrzValue);
        addCheck(n, field, new Check("MRZ_MATCH", ok));
    }

    private static void compareDate(Map<String, FieldRules.Normalized> n, String field, LocalDate d) {
        FieldRules.Normalized f = n.get(field);
        if (f != null && f.date() != null) {
            addCheck(n, field, new Check("MRZ_MATCH", f.date().equals(d)));
        }
    }

    private static void addCheck(Map<String, FieldRules.Normalized> n, String field, Check c) {
        FieldRules.Normalized f = n.get(field);
        if (f != null && f.value() != null) {
            List<Check> checks = new ArrayList<>(f.checks());
            checks.add(c);
            n.put(field, new FieldRules.Normalized(f.value(), checks, f.date()));
        }
    }

    private static LocalDate date(Map<String, FieldRules.Normalized> n, String field) {
        FieldRules.Normalized f = n.get(field);
        return f == null ? null : f.date();
    }

    private static String str(JsonNode m, String k) {
        JsonNode v = m.path(k);
        return v.isNull() || v.isMissingNode() || v.asText().isBlank() ? null : v.asText();
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
