package ru.proxodnaya.shift.api.controller.impl;

import java.time.Instant;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.PassStatsController;
import ru.proxodnaya.shift.api.dto.response.LoadByHourRs;
import ru.proxodnaya.shift.api.dto.response.RefusalsRs;
import ru.proxodnaya.shift.api.mapper.PassStatsApiMapper;
import ru.proxodnaya.shift.features.analytics.query.service.PassStatsQueryService;

/**
 * Реализация API статистики проходов.
 */
@RestController
public class PassStatsControllerImpl implements PassStatsController {

    private final PassStatsQueryService stats;
    private final PassStatsApiMapper mapper;

    public PassStatsControllerImpl(PassStatsQueryService stats, PassStatsApiMapper mapper) {
        this.stats = stats;
        this.mapper = mapper;
    }

    @Override
    public RefusalsRs refusals(Instant from, Instant until, String checkpoint, boolean simulated) {
        return mapper.convert(stats.refusals(from, until, checkpoint, simulated));
    }

    @Override
    public LoadByHourRs loadByHour(Instant from, Instant until, String checkpoint, boolean simulated) {
        return mapper.convert(stats.loadByHour(from, until, checkpoint, simulated));
    }
}
