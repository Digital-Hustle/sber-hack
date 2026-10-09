package ru.proxodnaya.shift.api.controller.impl;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.IntervalController;
import ru.proxodnaya.shift.api.dto.response.IntervalPageRs;
import ru.proxodnaya.shift.api.mapper.ShiftApiMapper;
import ru.proxodnaya.shift.features.interval.model.IntervalFilter;
import ru.proxodnaya.shift.features.interval.query.service.IntervalQueryService;
import ru.proxodnaya.shift.shared.Period;

/**
 * Реализация API интервалов.
 */
@RestController
public class IntervalControllerImpl implements IntervalController {

    private final IntervalQueryService query;
    private final ShiftApiMapper mapper;
    private final Clock clock;

    public IntervalControllerImpl(IntervalQueryService query, ShiftApiMapper mapper, Clock clock) {
        this.query = query;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public IntervalPageRs list(Instant from, Instant until, String worker, String zone, boolean simulated, String cursor,
                               Integer limit) {
        Instant now = clock.instant();
        return mapper.convertIntervals(query.page(new IntervalFilter(Period.of(from, until), worker, zone, simulated), cursor, limit),
                row -> query.incomplete(row, now));
    }
}
