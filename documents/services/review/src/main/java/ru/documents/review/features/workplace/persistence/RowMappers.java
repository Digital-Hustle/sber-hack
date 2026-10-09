package ru.documents.review.features.workplace.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.documents.review.features.workplace.model.AppRow;
import ru.documents.review.features.workplace.model.DocRow;
import ru.documents.review.features.workplace.model.FieldOverride;
import ru.documents.review.features.workplace.model.HistoryEntry;

/**
 * Общие маппинги строк проекции (используются и командной, и читающей стороной).
 */
@Component
public class RowMappers {

    /** Колонки applications. */
    public static final String APP_COLUMNS = "a.id, a.status, a.source, a.required_types, a.missing, a.applicant_name, "
            + "a.assignee, a.assigned_at, a.created_at, a.submitted_at, a.decided_at, a.updated_at";

    /** Колонки documents. */
    public static final String DOC_COLUMNS = "d.id, d.application_id, d.slot_type, d.type, d.type_confidence, d.stage, "
            + "d.reason_code, d.reasons, d.result, d.result_id, d.expires_on, d.confirmed_by, d.confirmed_at, "
            + "d.removed, d.received_at, d.parsed_at, d.updated_at";

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final ObjectMapper json;

    public RowMappers(ObjectMapper json) {
        this.json = json;
    }

    /** Заявка. */
    public AppRow app(ResultSet rs, int n) throws SQLException {
        return new AppRow(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("source"),
                strings(rs.getString("required_types")), strings(rs.getString("missing")),
                rs.getString("applicant_name"), rs.getString("assignee"), ts(rs, "assigned_at"), ts(rs, "created_at"),
                ts(rs, "submitted_at"), ts(rs, "decided_at"), ts(rs, "updated_at"));
    }

    /** Документ. */
    public DocRow doc(ResultSet rs, int n) throws SQLException {
        Date exp = rs.getDate("expires_on");
        double c = rs.getDouble("type_confidence");
        Double conf = rs.wasNull() ? null : c;
        return new DocRow(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getString("slot_type"), rs.getString("type"), conf, rs.getString("stage"),
                rs.getString("reason_code"), node(rs.getString("reasons")), node(rs.getString("result")),
                rs.getString("result_id"), exp == null ? null : exp.toLocalDate(), rs.getString("confirmed_by"),
                ts(rs, "confirmed_at"), rs.getBoolean("removed"), ts(rs, "received_at"), ts(rs, "parsed_at"),
                ts(rs, "updated_at"));
    }

    /** Правка. */
    public FieldOverride override(ResultSet rs, int n) throws SQLException {
        return new FieldOverride(rs.getString("name"), rs.getString("value"), rs.getString("author"),
                ts(rs, "created_at"));
    }

    /** Запись истории. */
    public HistoryEntry history(ResultSet rs, int n) throws SQLException {
        return new HistoryEntry(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                rs.getString("actor"), rs.getString("source"), rs.getString("action"), node(rs.getString("details")),
                ts(rs, "created_at"));
    }

    /**
     * JSON в строку для jsonb-параметра.
     *
     * @param v значение
     * @return строка JSON
     */
    public String write(Object v) {
        try {
            return json.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json", e);
        }
    }

    /**
     * Строка → JSON.
     *
     * @param s строка
     * @return узел или null
     */
    public JsonNode node(String s) {
        if (s == null) {
            return null;
        }
        try {
            return json.readTree(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json", e);
        }
    }

    /**
     * Время для параметра.
     *
     * @param i момент
     * @return timestamp
     */
    public static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private List<String> strings(String s) {
        if (s == null) {
            return List.of();
        }
        try {
            return json.readValue(s, STRINGS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json", e);
        }
    }

    private static Instant ts(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }
}
