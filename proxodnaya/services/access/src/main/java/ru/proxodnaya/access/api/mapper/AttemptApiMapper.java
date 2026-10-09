package ru.proxodnaya.access.api.mapper;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.api.dto.response.AttemptPageRs;
import ru.proxodnaya.access.api.dto.response.AttemptRs;
import ru.proxodnaya.access.api.dto.response.PageRs;
import ru.proxodnaya.access.features.pass.query.model.AttemptPage;
import ru.proxodnaya.access.features.pass.query.model.AttemptView;

/**
 * Журнал → DTO. Вручную: текст причины берётся из {@code ReasonCode}, MapStruct здесь ничего не упрощает.
 */
@Component
public class AttemptApiMapper {

    /**
     * Страница журнала.
     *
     * @param page страница
     * @return DTO
     */
    public AttemptPageRs convert(AttemptPage page) {
        return new AttemptPageRs(page.items().stream().map(AttemptApiMapper::convert).toList(), new PageRs(page.next(), page.total()));
    }

    private static AttemptRs convert(AttemptView row) {
        return new AttemptRs(row.id().toString(), row.ts(), row.checkpointId(), row.zoneId(), row.direction().name(), row.workerId(),
                row.decision().name(), row.code().name(), row.code().message(), row.source().name(), row.faceScore(), row.latencyMs(),
                row.note(), row.guardId());
    }
}
