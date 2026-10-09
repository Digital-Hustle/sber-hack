package ru.proxodnaya.access.api.controller.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.InternalStatsController;
import ru.proxodnaya.access.api.dto.response.AccessStatsRs;
import ru.proxodnaya.access.features.pass.query.model.AccessStats;
import ru.proxodnaya.access.features.pass.query.service.PassQueryService;

@RestController
public class InternalStatsControllerImpl implements InternalStatsController {

    private final PassQueryService queryService;

    public InternalStatsControllerImpl(PassQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public AccessStatsRs stats(Instant from, Instant to) {
        AccessStats stats = queryService.stats(from, to);
        List<AccessStatsRs.RefusalStat> refusals = stats.refusalsByCode().stream()
                .map(r -> new AccessStatsRs.RefusalStat(r.code(), r.count(), r.system()))
                .toList();
        List<AccessStatsRs.HourLoad> load = stats.loadByHour().stream()
                .map(h -> new AccessStatsRs.HourLoad(h.hour(), h.in(), h.out()))
                .toList();
        return new AccessStatsRs(stats.from(), stats.to(), stats.totalAttempts(),
                stats.allow(), stats.deny(), stats.denySystem(), refusals, load);
    }
}
