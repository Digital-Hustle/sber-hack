package ru.documents.docproc.api.mapper;

import org.springframework.stereotype.Component;

import ru.documents.docproc.api.dto.response.ResultRs;
import ru.documents.docproc.features.processing.model.ResultView;

/**
 * Модель результата → ответ API.
 */
@Component
public class ResultMapper {

    /** Преобразовать. */
    public ResultRs convert(ResultView v) {
        return new ResultRs(v.documentId(), v.applicationId(), v.stage(), v.type(), v.typeName(), v.typeConfidence(),
                v.evidence(), v.reasons(), v.fields().stream().map(f -> new ResultRs.Field(f.name(), f.title(), f.value(),
                f.raw(), f.page(), f.bbox(), f.confidence(), f.checks(), f.reason())).toList(), v.quality(), v.mrz(),
                v.pages(), v.model(), v.promptVersion(), v.schemaVersion(), v.durations(), v.attempts(), v.lastError(),
                v.createdAt());
    }
}
