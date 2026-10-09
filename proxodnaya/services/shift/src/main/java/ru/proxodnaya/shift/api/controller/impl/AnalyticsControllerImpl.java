package ru.proxodnaya.shift.api.controller.impl;

import java.time.Instant;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.AnalyticsController;
import ru.proxodnaya.shift.api.dto.response.HoursRs;
import ru.proxodnaya.shift.api.dto.response.LateOvertimeRs;
import ru.proxodnaya.shift.api.dto.response.SummaryRs;
import ru.proxodnaya.shift.api.mapper.ShiftApiMapper;
import ru.proxodnaya.shift.features.analytics.query.service.AnalyticsQueryService;

/**
 * Реализация API аналитики.
 */
@RestController
public class AnalyticsControllerImpl implements AnalyticsController {

    private final AnalyticsQueryService analytics;
    private final ShiftApiMapper mapper;

    public AnalyticsControllerImpl(AnalyticsQueryService analytics, ShiftApiMapper mapper) {
        this.analytics = analytics;
        this.mapper = mapper;
    }

    @Override
    public SummaryRs summary() {
        return mapper.convert(analytics.summary());
    }

    @Override
    public HoursRs hours(Instant from, Instant until, String worker, String zone, boolean simulated) {
        return mapper.convert(analytics.hours(from, until, worker, zone, simulated));
    }

    @Override
    public LateOvertimeRs lateOvertime(Instant from, Instant until, String worker, boolean simulated) {
        return mapper.convert(analytics.lateOvertime(from, until, worker, simulated));
    }
}
