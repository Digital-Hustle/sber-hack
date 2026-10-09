package ru.documents.review.features.workplace.query.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.documents.review.features.workplace.model.AppRow;
import ru.documents.review.features.workplace.model.Decision;
import ru.documents.review.features.workplace.model.DocRow;
import ru.documents.review.features.workplace.model.FieldOverride;
import ru.documents.review.features.workplace.model.HistoryEntry;
import ru.documents.review.features.workplace.model.Metrics;
import ru.documents.review.features.workplace.model.QueueFilter;
import ru.documents.review.features.workplace.model.QueueItem;
import ru.documents.review.features.workplace.persistence.RowMappers;

/**
 * Чтение: очередь, карточка, история, отчёты, метрики — SQL по реальным таблицам.
 */
@Repository
public class ReadRepository {

    private static final String FINAL = "('PARSED','NEEDS_REVIEW','UNREADABLE','FAILED')";
    private static final String ATTENTION = "('NEEDS_REVIEW','UNREADABLE','FAILED')";

    private final JdbcClient jdbc;
    private final RowMappers rows;

    public ReadRepository(JdbcClient jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    /** Заявка. */
    public Optional<AppRow> app(UUID id) {
        return jdbc.sql("select " + RowMappers.APP_COLUMNS + " from applications a where a.id = ?").param(id)
                .query(rows::app).optional();
    }

    /** Документы заявки. */
    public List<DocRow> docs(UUID app) {
        return jdbc.sql("select " + RowMappers.DOC_COLUMNS + " from documents d where d.application_id = ? "
                + "and not d.removed order by d.received_at, d.id").param(app).query(rows::doc).list();
    }

    /** Последние правки по документам заявки. */
    public Map<UUID, List<FieldOverride>> overrides(UUID app) {
        Map<UUID, List<FieldOverride>> out = new HashMap<>();
        jdbc.sql("""
                select distinct on (o.document_id, o.name) o.document_id, o.name, o.value, o.author, o.created_at
                from field_overrides o join documents d on d.id = o.document_id
                where d.application_id = ? order by o.document_id, o.name, o.created_at desc""").param(app)
                .query((rs, n) -> {
                    out.computeIfAbsent(rs.getObject("document_id", UUID.class), k -> new ArrayList<>())
                            .add(rows.override(rs, n));
                    return null;
                }).list();
        return out;
    }

    /** Последнее решение. */
    public Optional<Decision> decision(UUID app) {
        return jdbc.sql("select decision, comment, actor, created_at from decisions where application_id = ? "
                + "order by created_at desc limit 1").param(app)
                .query((rs, n) -> new Decision(rs.getString("decision"), rs.getString("comment"),
                        rs.getString("actor"), rs.getTimestamp("created_at").toInstant())).optional();
    }

    /** История (новые сверху). */
    public List<HistoryEntry> history(UUID app, int limit) {
        return jdbc.sql("select * from application_history where application_id = ? order by created_at desc, id desc "
                + "limit ?").params(app, limit).query(rows::history).list();
    }

    /** Очередь. */
    public List<QueueItem> queue(QueueFilter f, String me, Instant now) {
        Where w = where(f, me);
        String order = "updated".equals(f.sort()) ? "a.updated_at desc, a.id" : "a.submitted_at nulls last, a.id";
        List<Object> params = new ArrayList<>();
        params.add(RowMappers.ts(now));
        params.addAll(w.params());
        params.add(f.limit());
        params.add(f.offset());
        return jdbc.sql("""
                select a.id, a.status, a.source, a.applicant_name, a.submitted_at, a.assignee, a.missing, a.updated_at,
                    case when a.submitted_at is not null and a.status not in ('ACCEPTED','RETURNED','DRAFT')
                        then extract(epoch from (?::timestamptz - a.submitted_at))::bigint end as waiting,
                    (select count(*) from documents d where d.application_id = a.id and not d.removed) as docs,
                    (select count(*) from documents d where d.application_id = a.id and not d.removed
                        and d.stage not in %s) as processing,
                    (select count(*) from documents d where d.application_id = a.id and not d.removed
                        and d.stage in %s and d.confirmed_at is null) as attention,
                    (select count(*) from documents d where d.application_id = a.id and not d.removed
                        and d.confirmed_at is not null) as confirmed
                from applications a %s order by %s limit ? offset ?""".formatted(FINAL, ATTENTION, w.sql(), order))
                .params(params)
                .query((rs, n) -> {
                    long waiting = rs.getLong("waiting");
                    Long wait = rs.wasNull() ? null : waiting;
                    Timestamp sub = rs.getTimestamp("submitted_at");
                    return new QueueItem(rs.getObject("id", UUID.class), rs.getString("status"),
                            rs.getString("source"), rs.getString("applicant_name"),
                            sub == null ? null : sub.toInstant(), wait, rs.getString("assignee"), rs.getInt("docs"),
                            rs.getInt("processing"), rs.getInt("attention"), rs.getInt("confirmed"),
                            strings(rs.getString("missing")), rs.getTimestamp("updated_at").toInstant());
                }).list();
    }

    /** Всего по фильтру. */
    public long count(QueueFilter f, String me) {
        Where w = where(f, me);
        return jdbc.sql("select count(*) from applications a " + w.sql()).params(w.params()).query(Long.class)
                .single();
    }

    /** Документы, истекающие до даты (B-04). */
    public List<Map<String, Object>> expiring(LocalDate until) {
        return jdbc.sql("""
                select d.application_id, d.id, d.type, d.result ->> 'typeName' as type_name, a.applicant_name,
                    a.status, d.expires_on
                from documents d join applications a on a.id = d.application_id
                where not d.removed and d.expires_on is not null and d.expires_on <= ? and a.status <> 'DRAFT'
                order by d.expires_on, d.id""").param(until).query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("applicationId", rs.getObject("application_id", UUID.class));
                    m.put("documentId", rs.getObject("id", UUID.class));
                    m.put("type", rs.getString("type"));
                    m.put("typeName", rs.getString("type_name"));
                    m.put("applicantName", rs.getString("applicant_name"));
                    m.put("status", rs.getString("status"));
                    m.put("expiresOn", rs.getDate("expires_on").toLocalDate());
                    return m;
                }).list();
    }

    /** Заявки по статусам. */
    public Map<String, Long> appsByStatus() {
        return grouped("select status as k, count(*) as c from applications group by status");
    }

    /** Документы по стадиям. */
    public Map<String, Long> docsByStage() {
        return grouped("select stage as k, count(*) as c from documents where not removed group by stage");
    }

    /** Ожидание решения у отправленных, не решённых заявок. */
    public Metrics.Percentiles waiting(Instant now) {
        return percentiles("""
                select count(*) as n,
                    percentile_cont(0.5) within group (order by extract(epoch from (?::timestamptz - submitted_at))),
                    percentile_cont(0.95) within group (order by extract(epoch from (?::timestamptz - submitted_at)))
                from applications where submitted_at is not null and status in ('SUBMITTED','IN_REVIEW')""",
                RowMappers.ts(now), RowMappers.ts(now));
    }

    /** От отправки до решения. */
    public Metrics.Percentiles decision() {
        return percentiles("""
                select count(*) as n,
                    percentile_cont(0.5) within group (order by extract(epoch from (decided_at - submitted_at))),
                    percentile_cont(0.95) within group (order by extract(epoch from (decided_at - submitted_at)))
                from applications where decided_at is not null and submitted_at is not null""");
    }

    /** Полей в подтверждённых документах. */
    public long confirmedFields() {
        return jdbc.sql("select coalesce(sum(jsonb_array_length(result -> 'fields')), 0) from documents "
                + "where confirmed_at is not null and not removed and result is not null").query(Long.class).single();
    }

    /** Из них исправлено оператором. */
    public long correctedFields() {
        return jdbc.sql("""
                select count(distinct (o.document_id, o.name)) from field_overrides o
                join documents d on d.id = o.document_id where d.confirmed_at is not null and not d.removed""")
                .query(Long.class).single();
    }

    private Metrics.Percentiles percentiles(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query((rs, n) -> {
            double p50 = rs.getDouble(2);
            Double a = rs.wasNull() ? null : p50;
            double p95 = rs.getDouble(3);
            Double b = rs.wasNull() ? null : p95;
            return new Metrics.Percentiles(rs.getLong(1), a, b);
        }).single();
    }

    private Map<String, Long> grouped(String sql) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.sql(sql).query((rs, n) -> out.put(rs.getString("k"), rs.getLong("c"))).list();
        return out;
    }

    private record Where(String sql, List<Object> params) {
    }

    private static Where where(QueueFilter f, String me) {
        List<String> cond = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (f.statuses().isEmpty()) {
            cond.add("a.status <> 'DRAFT'");
        } else {
            cond.add("a.status in (" + String.join(",", f.statuses().stream().map(s -> "?").toList()) + ")");
            params.addAll(f.statuses());
        }
        if (f.q() != null && !f.q().isBlank()) {
            cond.add("(a.applicant_name ilike ? or a.id::text like ?)");
            params.add("%" + f.q().trim() + "%");
            params.add(f.q().trim().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if ("me".equals(f.assignee())) {
            cond.add("a.assignee = ?");
            params.add(me);
        } else if ("none".equals(f.assignee())) {
            cond.add("a.assignee is null");
        } else if (f.assignee() != null && !f.assignee().isBlank()) {
            cond.add("a.assignee = ?");
            params.add(f.assignee());
        }
        if (f.attention()) {
            cond.add("exists (select 1 from documents d where d.application_id = a.id and not d.removed "
                    + "and d.stage in " + ATTENTION + " and d.confirmed_at is null)");
        }
        return new Where("where " + String.join(" and ", cond), params);
    }

    private List<String> strings(String s) {
        List<String> out = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode n = rows.node(s);
        if (n != null) {
            n.forEach(x -> out.add(x.asText()));
        }
        return out;
    }
}
