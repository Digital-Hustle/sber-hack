package ru.proxodnaya.people.api.controller.impl;

import java.util.UUID;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.people.api.controller.SiteController;
import ru.proxodnaya.people.api.dto.request.CreateCheckpointRq;
import ru.proxodnaya.people.api.dto.request.CreateSiteRq;
import ru.proxodnaya.people.api.dto.request.CreateZoneRq;
import ru.proxodnaya.people.api.dto.response.CheckpointListRs;
import ru.proxodnaya.people.api.dto.response.CheckpointRs;
import ru.proxodnaya.people.api.dto.response.SiteListRs;
import ru.proxodnaya.people.api.dto.response.SiteRs;
import ru.proxodnaya.people.api.dto.response.ZoneListRs;
import ru.proxodnaya.people.api.dto.response.ZoneRs;
import ru.proxodnaya.people.api.mapper.SiteApiMapper;
import ru.proxodnaya.people.features.site.command.service.SiteCommandService;
import ru.proxodnaya.people.features.site.query.service.SiteQueryService;

/**
 * Реализация API объектов.
 */
@RestController
public class SiteControllerImpl implements SiteController {

    private final SiteCommandService commands;
    private final SiteQueryService queries;
    private final SiteApiMapper mapper;

    public SiteControllerImpl(SiteCommandService commands, SiteQueryService queries, SiteApiMapper mapper) {
        this.commands = commands;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override
    public SiteListRs sites() {
        return new SiteListRs(mapper.toSites(queries.sites()));
    }

    @Override
    public SiteRs createSite(CreateSiteRq request) {
        return mapper.toRs(commands.createSite(request.name(), request.address()));
    }

    @Override
    public ZoneListRs zones(UUID siteId) {
        return new ZoneListRs(mapper.toZones(queries.zones(siteId)));
    }

    @Override
    public ZoneRs createZone(CreateZoneRq request) {
        return mapper.toRs(commands.createZone(request.siteId(), request.name(), request.capacity()));
    }

    @Override
    public CheckpointListRs checkpoints(UUID zoneId) {
        return new CheckpointListRs(mapper.toCheckpoints(queries.checkpoints(zoneId)));
    }

    @Override
    public CheckpointRs createCheckpoint(CreateCheckpointRq request) {
        return mapper.toRs(commands.createCheckpoint(request.zoneId(), request.name()));
    }
}
