package ru.documents.review.api.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.documents.review.api.dto.request.FieldsRq;
import ru.documents.review.api.dto.request.ResubmitRq;
import ru.documents.review.api.dto.response.CardRs;
import ru.documents.review.api.dto.response.MetricsRs;
import ru.documents.review.api.dto.response.PageRs;
import ru.documents.review.api.dto.response.QueueRs;
import ru.documents.review.features.workplace.command.service.ActionService;
import ru.documents.review.features.workplace.model.CardView;
import ru.documents.review.features.workplace.model.Metrics;
import ru.documents.review.features.workplace.model.QueuePage;

/**
 * Модели фичи ↔ DTO.
 */
@Component
public class CardMapper {

    /** Карточка. */
    public CardRs card(CardView c) {
        return new CardRs(c.application(), c.profile(), c.requests(), c.contactPhone(), c.contactEmail(),
                c.documents(), c.findings(), c.decision(), c.history(), c.counters());
    }

    /** Очередь. */
    public QueueRs queue(QueuePage p) {
        return new QueueRs(p.items(), new PageRs(p.next(), p.total()));
    }

    /** Метрики. */
    public MetricsRs metrics(Metrics m) {
        return new MetricsRs(m.applications(), m.documents(), m.waiting(), m.decision(), m.fieldsTotal(),
                m.fieldsCorrected(), m.formulas(), m.computedAt());
    }

    /** Правки. */
    public List<ActionService.FieldEdit> edits(FieldsRq rq) {
        return rq.fields().stream().map(f -> new ActionService.FieldEdit(f.name(), f.value())).toList();
    }

    /** Пункты запроса. */
    public List<ActionService.RequestItem> items(ResubmitRq rq) {
        return rq.items().stream().map(i -> new ActionService.RequestItem(i.documentId(), i.type(), i.reason()))
                .toList();
    }
}
