package ru.documents.review.features.workplace.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.documents.common.exception.DomainException;
import ru.documents.review.config.ReviewProperties;
import ru.documents.review.features.workplace.model.Actor;
import ru.documents.review.features.workplace.model.AppRow;
import ru.documents.review.features.workplace.model.CardView;
import ru.documents.review.features.workplace.model.DocRow;
import ru.documents.review.features.workplace.model.DocumentCard;
import ru.documents.review.features.workplace.model.ExpiringItem;
import ru.documents.review.features.workplace.model.FieldCard;
import ru.documents.review.features.workplace.model.FieldOverride;
import ru.documents.review.features.workplace.model.Finding;
import ru.documents.review.features.workplace.model.HistoryEntry;
import ru.documents.review.features.workplace.model.Metrics;
import ru.documents.review.features.workplace.model.QueueFilter;
import ru.documents.review.features.workplace.model.QueueItem;
import ru.documents.review.features.workplace.model.QueuePage;
import ru.documents.review.features.workplace.query.repository.ReadRepository;
import ru.documents.review.features.workplace.rules.CrossChecker;
import ru.documents.review.features.workplace.rules.DocFacts;
import ru.documents.review.features.workplace.rules.DocTypes;
import ru.documents.review.features.workplace.rules.Fields;
import ru.documents.review.features.workplace.rules.Masking;

/**
 * Чтение для оператора: очередь, карточка, история, отчёт, метрики.
 */
@Service
public class QueryService {

    private static final Set<String> ATTENTION_STAGES = Set.of("NEEDS_REVIEW", "UNREADABLE", "FAILED");
    private static final int HISTORY_IN_CARD = 100;
    private static final int HISTORY_MAX = 1000;
    private static final int MAX_LIMIT = 100;

    private final ReadRepository read;
    private final ReviewProperties props;
    private final Clock clock;

    public QueryService(ReadRepository read, ReviewProperties props, Clock clock) {
        this.read = read;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Очередь с курсором.
     *
     * @param statuses  статусы
     * @param q         поиск
     * @param assignee  me | none | логин
     * @param attention только требующие проверки
     * @param sort      waiting | updated
     * @param cursor    курсор
     * @param limit     размер страницы
     * @param me        кто смотрит
     * @return страница
     */
    public QueuePage queue(List<String> statuses, String q, String assignee, boolean attention, String sort,
                           String cursor, int limit, Actor me) {
        int offset = decode(cursor);
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        QueueFilter f = new QueueFilter(statuses, q, assignee, attention, sort, offset, size);
        List<QueueItem> items = read.queue(f, me.username(), clock.instant());
        long total = read.count(f, me.username());
        if (me.auditorOnly()) {
            items = items.stream().map(i -> new QueueItem(i.id(), i.status(), i.source(), Masking.text(i.applicantName()),
                    i.submittedAt(), i.waitingSeconds(), i.assignee(), i.documents(), i.processing(), i.attention(),
                    i.confirmed(), i.missing(), i.updatedAt())).toList();
        }
        String next = offset + items.size() < total ? encode(offset + items.size()) : null;
        return new QueuePage(items, next, total);
    }

    /**
     * Документы заявки, которым нужен результат из docproc в кэш.
     *
     * @param app заявка
     * @return документы без кэша
     */
    public List<UUID> uncached(UUID app) {
        return read.docs(app).stream().filter(d -> d.finished() && d.result() == null).map(DocRow::id).toList();
    }

    /**
     * Заявка есть в проекции.
     *
     * @param app заявка
     * @return строка
     */
    public AppRow app(UUID app) {
        return read.app(app).orElseThrow(DomainException::notFound);
    }

    /**
     * Карточка.
     *
     * @param id       заявка
     * @param summary  сводка intake (анкета, файлы, запросы) или null
     * @param unmasked без маскирования номеров (выгрузка для кадров)
     * @return карточка
     */
    public CardView card(UUID id, JsonNode summary, boolean unmasked) {
        AppRow a = app(id);
        List<DocRow> docs = read.docs(id);
        Map<UUID, List<FieldOverride>> ov = read.overrides(id);
        Map<String, JsonNode> files = new HashMap<>();
        if (summary != null) {
            for (JsonNode d : summary.path("documents")) {
                files.put(d.path("id").asText(), d.path("files"));
            }
        }
        List<DocFacts> facts = new ArrayList<>();
        List<DocumentCard> cards = new ArrayList<>();
        int total = 0;
        int attention = 0;
        int corrected = 0;
        for (DocRow d : docs) {
            List<FieldOverride> mine = ov.getOrDefault(d.id(), List.of());
            DocFacts f = Fields.facts(d, mine);
            facts.add(f.type() == null && d.slotType() != null && d.result() == null
                    ? new DocFacts(f.id(), d.slotType(), DocTypes.name(d.slotType()), f.values()) : f);
            List<FieldCard> fields = fields(d, mine, unmasked);
            total += fields.size();
            attention += (int) fields.stream().filter(FieldCard::attention).count();
            corrected += (int) fields.stream().filter(FieldCard::corrected).count();
            boolean confirmed = d.confirmedAt() != null;
            boolean att = !confirmed && (ATTENTION_STAGES.contains(d.stage())
                    || fields.stream().anyMatch(FieldCard::attention));
            JsonNode r = d.result();
            cards.add(new DocumentCard(d.id(), d.slotType(), d.type(), f.typeName() != null ? f.typeName()
                    : DocTypes.name(d.type()), d.typeConfidence(), d.stage(), d.reasonCode(),
                    d.reasons() != null ? d.reasons() : r == null ? null : r.get("reasons"),
                    files.get(d.id().toString()), r == null ? null : r.get("pages"),
                    r == null ? null : r.get("quality"), r == null ? null : text(r, "model"),
                    r == null ? null : text(r, "promptVersion"), d.expiresOn(), confirmed, d.confirmedBy(),
                    d.confirmedAt(), att, fields));
        }
        cards.sort(Comparator.comparing((DocumentCard c) -> !c.attention()));
        List<Finding> findings = CrossChecker.check(facts, a.requiredTypes());
        int confirmedDocs = (int) cards.stream().filter(DocumentCard::confirmed).count();
        return new CardView(a, summary == null ? null : summary.get("profile"),
                summary == null ? null : summary.get("requests"),
                summary == null ? null : text(summary, "contactPhone"),
                summary == null ? null : text(summary, "contactEmail"), cards, findings,
                read.decision(id).orElse(null), read.history(id, HISTORY_IN_CARD),
                new CardView.Counters(total, attention, corrected, confirmedDocs, cards.size()));
    }

    /**
     * История заявки (аудитор видит маски вместо значений).
     *
     * @param id заявка
     * @param me кто смотрит
     * @return записи, новые сверху
     */
    public List<HistoryEntry> history(UUID id, Actor me) {
        app(id);
        List<HistoryEntry> list = read.history(id, HISTORY_MAX);
        if (!me.auditorOnly()) {
            return list;
        }
        return list.stream().map(QueryService::maskHistory).toList();
    }

    /**
     * Истекающие документы (B-04).
     *
     * @param days горизонт, дней (null — из настроек)
     * @return строки
     */
    public List<ExpiringItem> expiring(Integer days) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(props.zone())));
        int horizon = days == null ? props.expiringDays() : Math.max(0, days);
        return read.expiring(today.plusDays(horizon)).stream().map(m -> {
            LocalDate on = (LocalDate) m.get("expiresOn");
            String type = (String) m.get("type");
            String name = m.get("typeName") != null ? (String) m.get("typeName") : DocTypes.name(type);
            return new ExpiringItem((UUID) m.get("applicationId"), (UUID) m.get("documentId"), type, name,
                    (String) m.get("applicantName"), (String) m.get("status"), on, ChronoUnit.DAYS.between(today, on));
        }).toList();
    }

    /**
     * Очередь в цифрах (B-06).
     *
     * @return метрики
     */
    public Metrics metrics() {
        Instant now = clock.instant();
        Map<String, String> formulas = new LinkedHashMap<>();
        formulas.put("applications", "count(*) по applications.status (черновики DRAFT — ещё не отправлены)");
        formulas.put("documents", "count(*) по documents.stage без удалённых соискателем");
        formulas.put("waiting", "now − submitted_at для заявок SUBMITTED и IN_REVIEW; percentile_cont 0.5 и 0.95");
        formulas.put("decision", "decided_at − submitted_at (последняя отправка) для решённых заявок; p50 и p95");
        formulas.put("fields", "поля подтверждённых документов; исправлено — есть хотя бы одна правка оператора");
        return new Metrics(read.appsByStatus(), read.docsByStage(), read.waiting(now), read.decision(),
                read.confirmedFields(), read.correctedFields(), formulas, now);
    }

    private List<FieldCard> fields(DocRow d, List<FieldOverride> overrides, boolean unmasked) {
        List<FieldCard> out = new ArrayList<>();
        if (d.result() == null) {
            return out;
        }
        Map<String, FieldOverride> byName = new HashMap<>();
        overrides.forEach(o -> byName.put(o.name(), o));
        for (JsonNode f : d.result().path("fields")) {
            String name = f.path("name").asText();
            String model = text(f, "value");
            FieldOverride o = byName.get(name);
            String value = o != null ? o.value() : model;
            Double conf = f.hasNonNull("confidence") ? f.get("confidence").asDouble() : null;
            boolean failed = false;
            for (JsonNode c : f.path("checks")) {
                failed |= !c.path("ok").asBoolean(true);
            }
            boolean weak = conf != null && conf < props.fieldReviewThreshold();
            boolean empty = model == null && f.hasNonNull("reason");
            boolean att = o == null && (failed || weak || empty);
            boolean mask = !unmasked && Masking.isNumber(name);
            String raw = text(f, "raw");
            out.add(new FieldCard(name, text(f, "title"), mask ? Masking.number(value) : value,
                    mask ? Masking.number(model) : model, mask ? Masking.number(raw) : raw,
                    f.hasNonNull("page") ? f.get("page").asInt() : null, f.get("bbox"), conf, f.get("checks"),
                    text(f, "reason"), o != null, o == null ? null : o.author(), o == null ? null : o.createdAt(),
                    mask && value != null, att));
        }
        out.sort(Comparator.comparing((FieldCard c) -> !c.attention()));
        return out;
    }

    private static HistoryEntry maskHistory(HistoryEntry h) {
        if (h.details() == null || !h.details().isObject()) {
            return h;
        }
        ObjectNode d = h.details().deepCopy();
        String field = d.path("field").asText("");
        for (String k : List.of("from", "to", "modelValue")) {
            if (d.hasNonNull(k)) {
                d.put(k, Masking.any(field, d.get(k).asText()));
            }
        }
        if (d.hasNonNull("comment")) {
            d.set("comment", JsonNodeFactory.instance.textNode("•••"));
        }
        return new HistoryEntry(h.id(), h.documentId(), h.actor(), h.source(), h.action(), d, h.createdAt());
    }

    private static String text(JsonNode n, String k) {
        return n.hasNonNull(k) ? n.get(k).asText() : null;
    }

    private static int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            throw DomainException.badRequest("CURSOR_INVALID", "Неверный курсор", "Начните с первой страницы");
        }
    }

    private static String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(offset)
                .getBytes(StandardCharsets.UTF_8));
    }
}
