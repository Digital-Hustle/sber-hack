package ru.documents.review.features.workplace.command.service;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.documents.common.event.Topics;
import ru.documents.common.exception.DomainException;
import ru.documents.common.outbox.OutboxWriter;
import ru.documents.review.features.workplace.command.repository.ProjectionRepository;
import ru.documents.review.features.workplace.model.Actor;
import ru.documents.review.features.workplace.model.AppRow;
import ru.documents.review.features.workplace.model.DocRow;
import ru.documents.review.features.workplace.model.FieldOverride;
import ru.documents.review.features.workplace.rules.Fields;

/**
 * Действия оператора: назначение, правки, подтверждение, запрос, решение, показ номера.
 * Каждое действие — запись истории и событие review.application.v1 в одной транзакции.
 */
@Service
public class ActionService {

    private static final String APP = "applicationId";
    private static final String DOC = "documentId";
    private static final Set<String> DECISIONS = Set.of("ACCEPTED", "RETURNED");

    private final ProjectionRepository repo;
    private final HistoryWriter history;
    private final OutboxWriter outbox;
    private final ProjectionService projection;
    private final Clock clock;

    public ActionService(ProjectionRepository repo, HistoryWriter history, OutboxWriter outbox,
                         ProjectionService projection, Clock clock) {
        this.repo = repo;
        this.history = history;
        this.outbox = outbox;
        this.projection = projection;
        this.clock = clock;
    }

    /**
     * Правка поля.
     *
     * @param name  поле
     * @param value новое значение (null — очистить)
     */
    public record FieldEdit(String name, String value) {
    }

    /**
     * Пункт запроса «дослать/переснять».
     *
     * @param documentId документ
     * @param type       вид
     * @param reason     причина
     */
    public record RequestItem(UUID documentId, String type, String reason) {
    }

    /**
     * Взять заявку в работу.
     *
     * @param id    заявка
     * @param me    оператор
     * @param force перехватить у другого (старший)
     */
    @Transactional
    public void assign(UUID id, Actor me, boolean force) {
        AppRow a = repo.lockApp(id).orElseThrow(DomainException::notFound);
        ensureOpen(a);
        if (me.username().equals(a.assignee())) {
            return;
        }
        if (a.assignee() != null && !(force && me.senior())) {
            throw taken(a);
        }
        doAssign(a, me);
    }

    /**
     * Взять следующую свободную заявку (самую давнюю).
     *
     * @param me оператор
     * @return заявка или пусто
     */
    @Transactional
    public Optional<UUID> next(Actor me) {
        Optional<UUID> id = repo.nextFree();
        id.ifPresent(x -> doAssign(repo.lockApp(x).orElseThrow(DomainException::notFound), me));
        return id;
    }

    /**
     * Исправить поля (FR-53).
     *
     * @param docId документ
     * @param edits правки
     * @param me    оператор
     * @return заявка
     */
    @Transactional
    public UUID correct(UUID docId, List<FieldEdit> edits, Actor me) {
        DocRow d = workable(docId, me);
        if (!d.parsed()) {
            throw notParsed();
        }
        Map<String, String> model = Fields.model(d.result());
        Map<String, String> current = Fields.facts(d, repo.overrides(docId)).values();
        Map<String, String> titles = new HashMap<>();
        d.result().path("fields").forEach(f -> titles.put(f.path("name").asText(), f.path("title").asText()));
        Instant now = clock.instant();
        List<String> changed = new ArrayList<>();
        for (FieldEdit e : edits) {
            if (!model.containsKey(e.name())) {
                throw DomainException.badRequest("FIELD_UNKNOWN", "В документе нет поля «" + e.name() + "»",
                        "Обновите карточку");
            }
            String value = e.value() == null || e.value().isBlank() ? null : e.value().trim();
            if (Objects.equals(value, current.get(e.name()))) {
                continue;
            }
            repo.override(UuidCreator.getTimeOrderedEpoch(), docId, e.name(), value, me.username(), now);
            Map<String, Object> det = new HashMap<>();
            det.put("field", e.name());
            det.put("title", titles.get(e.name()));
            det.put("from", current.get(e.name()));
            det.put("to", value);
            det.put("modelValue", model.get(e.name()));
            history.add(d.applicationId(), docId, me.username(), HistoryWriter.OPERATOR, "FIELD_CORRECTED", det);
            changed.add(e.name());
        }
        if (!changed.isEmpty()) {
            repo.touch(d.applicationId(), now);
            projection.derive(d.applicationId());
            outbox.add(Topics.REVIEW_APPLICATION_V1, "FieldCorrected", d.applicationId().toString(),
                    Map.of(APP, d.applicationId().toString(), DOC, docId.toString(), "fields", changed,
                            "operator", me.username()));
        }
        return d.applicationId();
    }

    /**
     * Проверить, что документ можно отправить на смену вида или переразбор.
     *
     * @param docId документ
     * @param me    оператор
     * @return документ
     */
    @Transactional
    public DocRow prepare(UUID docId, Actor me) {
        return workable(docId, me);
    }

    /**
     * Записать смену вида (docproc уже принял задачу).
     *
     * @param doc  документ
     * @param type новый вид
     * @param me   оператор
     */
    @Transactional
    public void retyped(DocRow doc, String type, Actor me) {
        repo.reset(doc.id(), "EXTRACTING_FIELDS", clock.instant());
        Map<String, Object> det = new HashMap<>();
        det.put("from", doc.type());
        det.put("to", type);
        history.add(doc.applicationId(), doc.id(), me.username(), HistoryWriter.OPERATOR, "TYPE_CORRECTED", det);
        repo.stage(doc.id(), doc.applicationId(), "EXTRACTING_FIELDS", type, null, clock.instant());
        projection.derive(doc.applicationId());
        outbox.add(Topics.REVIEW_APPLICATION_V1, "TypeCorrected", doc.applicationId().toString(),
                Map.of(APP, doc.applicationId().toString(), DOC, doc.id().toString(), "type", type));
    }

    /**
     * Записать «разобрать заново».
     *
     * @param doc документ
     * @param me  оператор
     */
    @Transactional
    public void reprocessed(DocRow doc, Actor me) {
        repo.reset(doc.id(), "QUEUED", clock.instant());
        history.add(doc.applicationId(), doc.id(), me.username(), HistoryWriter.OPERATOR, "REPROCESS_REQUESTED",
                Map.of());
        projection.derive(doc.applicationId());
    }

    /**
     * Подтвердить документ.
     *
     * @param docId документ
     * @param me    оператор
     * @return заявка
     */
    @Transactional
    public UUID confirm(UUID docId, Actor me) {
        DocRow d = workable(docId, me);
        if (!d.parsed()) {
            throw notParsed();
        }
        repo.confirm(docId, me.username(), clock.instant());
        List<FieldOverride> ov = repo.overrides(docId);
        history.add(d.applicationId(), docId, me.username(), HistoryWriter.OPERATOR, "DOCUMENT_CONFIRMED",
                Map.of("corrected", ov.stream().map(FieldOverride::name).toList()));
        repo.touch(d.applicationId(), clock.instant());
        outbox.add(Topics.REVIEW_APPLICATION_V1, "DocumentConfirmed", d.applicationId().toString(),
                Map.of(APP, d.applicationId().toString(), DOC, docId.toString(), "operator", me.username()));
        return d.applicationId();
    }

    /**
     * Запросить у соискателя «дослать/переснять» (FR-55).
     *
     * @param id    заявка
     * @param items пункты
     * @param me    оператор
     */
    @Transactional
    public void request(UUID id, List<RequestItem> items, Actor me) {
        AppRow a = workableApp(id, me);
        Set<UUID> own = repo.docs(id).stream().map(DocRow::id).collect(Collectors.toSet());
        List<Map<String, Object>> payload = new ArrayList<>();
        for (RequestItem it : items) {
            if (it.documentId() != null && !own.contains(it.documentId())) {
                throw DomainException.badRequest("DOCUMENT_NOT_IN_APPLICATION", "Документ не из этой заявки",
                        "Обновите карточку");
            }
            Map<String, Object> m = new HashMap<>();
            if (it.documentId() != null) {
                m.put(DOC, it.documentId().toString());
            }
            if (it.type() != null) {
                m.put("type", it.type());
            }
            m.put("reason", it.reason().trim());
            payload.add(m);
        }
        repo.status(a.id(), "NEEDS_CHANGES", clock.instant());
        history.add(id, null, me.username(), HistoryWriter.OPERATOR, "RESUBMIT_REQUESTED", Map.of("items", payload));
        outbox.add(Topics.REVIEW_APPLICATION_V1, "ResubmitRequested", id.toString(),
                Map.of(APP, id.toString(), "items", payload));
    }

    /**
     * Решение по заявке.
     *
     * @param id       заявка
     * @param decision ACCEPTED | RETURNED
     * @param comment  комментарий (для RETURNED обязателен)
     * @param me       оператор
     */
    @Transactional
    public void decide(UUID id, String decision, String comment, Actor me) {
        if (!DECISIONS.contains(decision)) {
            throw DomainException.badRequest("DECISION_UNKNOWN", "Решение — ACCEPTED или RETURNED", null);
        }
        String text = comment == null || comment.isBlank() ? null : comment.trim();
        if ("RETURNED".equals(decision) && text == null) {
            throw DomainException.badRequest("COMMENT_REQUIRED", "Укажите причину возврата",
                    "Соискатель увидит этот текст");
        }
        workableApp(id, me);
        if ("ACCEPTED".equals(decision) && repo.docs(id).stream().anyMatch(d -> !d.finished())) {
            throw DomainException.conflict("DOCUMENTS_IN_PROGRESS", "Не все документы разобраны",
                    "Дождитесь окончания разбора");
        }
        repo.decide(id, UuidCreator.getTimeOrderedEpoch(), decision, text, me.username(), clock.instant());
        Map<String, Object> det = new HashMap<>();
        det.put("decision", decision);
        det.put("comment", text);
        history.add(id, null, me.username(), HistoryWriter.OPERATOR, "DECIDED", det);
        Map<String, Object> ev = new HashMap<>(det);
        ev.put(APP, id.toString());
        outbox.add(Topics.REVIEW_APPLICATION_V1, "ApplicationDecided", id.toString(), ev);
    }

    /**
     * Показать полный номер (B-07) — запись в аудит.
     *
     * @param id    заявка
     * @param docId документ
     * @param field поле
     * @param me    оператор
     * @return значение
     */
    @Transactional
    public String reveal(UUID id, UUID docId, String field, Actor me) {
        DocRow d = repo.doc(docId).filter(x -> x.applicationId().equals(id) && !x.removed())
                .orElseThrow(DomainException::notFound);
        Map<String, String> values = Fields.facts(d, repo.overrides(docId)).values();
        if (!values.containsKey(field)) {
            throw DomainException.notFound();
        }
        history.add(id, docId, me.username(), HistoryWriter.OPERATOR, "REVEALED", Map.of("field", field));
        return values.get(field);
    }

    /**
     * Отметить выгрузку карточки для кадров.
     *
     * @param id     заявка
     * @param format формат
     * @param me     оператор
     */
    @Transactional
    public void exported(UUID id, String format, Actor me) {
        history.add(id, null, me.username(), HistoryWriter.OPERATOR, "EXPORTED", Map.of("format", format));
    }

    private DocRow workable(UUID docId, Actor me) {
        DocRow d = repo.doc(docId).filter(x -> !x.removed()).orElseThrow(DomainException::notFound);
        workableApp(d.applicationId(), me);
        return repo.doc(docId).orElseThrow(DomainException::notFound);
    }

    private AppRow workableApp(UUID id, Actor me) {
        AppRow a = repo.lockApp(id).orElseThrow(DomainException::notFound);
        ensureOpen(a);
        if (a.assignee() == null) {
            doAssign(a, me);
        } else if (!a.assignee().equals(me.username()) && !me.senior()) {
            throw taken(a);
        }
        return a;
    }

    private void doAssign(AppRow a, Actor me) {
        repo.assign(a.id(), me.username(), clock.instant());
        Map<String, Object> det = new HashMap<>();
        det.put("from", a.assignee());
        det.put("to", me.username());
        history.add(a.id(), null, me.username(), HistoryWriter.OPERATOR, "ASSIGNED", det);
        outbox.add(Topics.REVIEW_APPLICATION_V1, "ApplicationAssigned", a.id().toString(),
                Map.of(APP, a.id().toString(), "assignee", me.username()));
    }

    private static void ensureOpen(AppRow a) {
        if (a.decided()) {
            throw DomainException.conflict("APPLICATION_DECIDED", "По заявке уже принято решение",
                    "Изменения после решения не принимаются");
        }
        if ("DRAFT".equals(a.status())) {
            throw DomainException.conflict("NOT_SUBMITTED", "Соискатель ещё не отправил заявку",
                    "Заявка появится в очереди после отправки");
        }
    }

    private static DomainException taken(AppRow a) {
        return new DomainException("ALREADY_ASSIGNED", 409, "Заявку уже проверяет " + a.assignee(),
                "Возьмите другую заявку", Map.of("assignee", a.assignee()));
    }

    private static DomainException notParsed() {
        return DomainException.conflict("NOT_PARSED_YET", "Документ ещё не разобран",
                "Карточка покажет поля, когда разбор закончится");
    }
}
