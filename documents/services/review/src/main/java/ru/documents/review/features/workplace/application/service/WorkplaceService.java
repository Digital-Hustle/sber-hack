package ru.documents.review.features.workplace.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.documents.common.exception.DomainException;
import ru.documents.review.features.workplace.command.service.ActionService;
import ru.documents.review.features.workplace.command.service.ProjectionService;
import ru.documents.review.features.workplace.gateway.DocprocGateway;
import ru.documents.review.features.workplace.gateway.IntakeGateway;
import ru.documents.review.features.workplace.model.Actor;
import ru.documents.review.features.workplace.model.CardView;
import ru.documents.review.features.workplace.model.DocRow;
import ru.documents.review.features.workplace.model.StreamEvent;
import ru.documents.review.features.workplace.query.service.QueryService;

/**
 * Сценарии оператора: внешние вызовы (intake, docproc) — вне транзакций, затем запись и SSE.
 */
@Service
public class WorkplaceService {

    private static final Set<String> FINISHED = Set.of("DocumentParsed", "DocumentUnreadable", "DocumentFailed");

    private final ActionService actions;
    private final ProjectionService projection;
    private final QueryService queries;
    private final DocprocGateway docproc;
    private final IntakeGateway intake;
    private final StreamHub hub;

    public WorkplaceService(ActionService actions, ProjectionService projection, QueryService queries,
                            DocprocGateway docproc, IntakeGateway intake, StreamHub hub) {
        this.actions = actions;
        this.projection = projection;
        this.queries = queries;
        this.docproc = docproc;
        this.intake = intake;
        this.hub = hub;
    }

    /**
     * Карточка: результаты docproc докачиваются в кэш, файлы и анкета — из intake.
     *
     * @param id       заявка
     * @param unmasked без маскирования (выгрузка)
     * @return карточка
     */
    public CardView card(UUID id, boolean unmasked) {
        queries.app(id);
        for (UUID doc : queries.uncached(id)) {
            refresh(doc);
        }
        JsonNode summary = intake.summary(id).orElse(null);
        return queries.card(id, summary, unmasked);
    }

    /**
     * Подтянуть результат документа из docproc.
     *
     * @param doc документ
     */
    public void refresh(UUID doc) {
        docproc.result(doc).ifPresent(r -> projection.cache(doc, r));
    }

    /**
     * Применить событие и разослать SSE.
     *
     * @param eventId id
     * @param type    тип
     * @param payload данные
     */
    public void onEvent(String eventId, String type, JsonNode payload) {
        Optional<StreamEvent> ev = projection.apply(eventId, type, payload);
        if (ev.isPresent() && FINISHED.contains(type)) {
            try {
                refresh(ev.get().documentId());
            } catch (DomainException e) {
                // docproc недоступен — карточка докачает результат при открытии.
            }
        }
        ev.ifPresent(hub::publish);
    }

    /** Взять в работу. */
    public void assign(UUID id, Actor me, boolean force) {
        actions.assign(id, me, force);
        hub.publish(StreamEvent.queue(id));
    }

    /** Следующая заявка. */
    public UUID next(Actor me) {
        UUID id = actions.next(me).orElseThrow(() -> new DomainException("QUEUE_EMPTY", 404,
                "Свободных заявок нет", "Новые появятся в очереди автоматически"));
        hub.publish(StreamEvent.queue(id));
        return id;
    }

    /** Правка полей. */
    public void correct(UUID doc, List<ActionService.FieldEdit> edits, Actor me) {
        hub.publish(StreamEvent.queue(actions.correct(doc, edits, me)));
    }

    /** Сменить вид → docproc переизвлечёт поля. */
    public void retype(UUID doc, String type, Actor me) {
        DocRow d = actions.prepare(doc, me);
        docproc.retype(doc, type);
        actions.retyped(d, type, me);
        hub.publish(new StreamEvent("document.stage", d.applicationId(), doc, "EXTRACTING_FIELDS"));
    }

    /** Разобрать заново. */
    public void reprocess(UUID doc, Actor me) {
        DocRow d = actions.prepare(doc, me);
        docproc.reprocess(doc);
        actions.reprocessed(d, me);
        hub.publish(new StreamEvent("document.stage", d.applicationId(), doc, "QUEUED"));
    }

    /** Подтвердить документ. */
    public void confirm(UUID doc, Actor me) {
        hub.publish(StreamEvent.queue(actions.confirm(doc, me)));
    }

    /** Запрос «дослать/переснять». */
    public void request(UUID id, List<ActionService.RequestItem> items, Actor me) {
        actions.request(id, items, me);
        hub.publish(StreamEvent.queue(id));
    }

    /** Решение. */
    public void decide(UUID id, String decision, String comment, Actor me) {
        actions.decide(id, decision, comment, me);
        hub.publish(StreamEvent.queue(id));
    }
}
