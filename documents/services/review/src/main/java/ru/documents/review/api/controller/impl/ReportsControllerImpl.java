package ru.documents.review.api.controller.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.documents.review.api.controller.ReportsController;
import ru.documents.review.api.dto.response.ExpiringRs;
import ru.documents.review.api.dto.response.MetricsRs;
import ru.documents.review.api.mapper.CardMapper;
import ru.documents.review.config.ReviewProperties;
import ru.documents.review.features.workplace.application.service.ExportService;
import ru.documents.review.features.workplace.query.service.QueryService;

/**
 * Отчёты и метрики.
 */
@RestController
public class ReportsControllerImpl implements ReportsController {

    private final QueryService queries;
    private final ExportService exports;
    private final CardMapper mapper;
    private final ReviewProperties props;

    public ReportsControllerImpl(QueryService queries, ExportService exports, CardMapper mapper,
                                 ReviewProperties props) {
        this.queries = queries;
        this.exports = exports;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public ExpiringRs expiring(Integer days) {
        return new ExpiringRs(days == null ? props.expiringDays() : days, queries.expiring(days));
    }

    @Override
    public ResponseEntity<byte[]> expiringXlsx(Integer days) {
        return ApplicationsControllerImpl.file(exports.expiring(queries.expiring(days)));
    }

    @Override
    public MetricsRs metrics() {
        return mapper.metrics(queries.metrics());
    }
}
