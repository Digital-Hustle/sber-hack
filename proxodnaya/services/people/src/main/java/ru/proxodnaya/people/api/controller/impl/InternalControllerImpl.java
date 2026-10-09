package ru.proxodnaya.people.api.controller.impl;

import java.util.UUID;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.people.api.controller.InternalController;
import ru.proxodnaya.people.api.dto.response.AccessProfileRs;
import ru.proxodnaya.people.api.dto.response.CheckpointRs;
import ru.proxodnaya.people.api.dto.response.InternalCheckpointRs;
import ru.proxodnaya.people.api.dto.response.WorkerRs;
import ru.proxodnaya.people.api.mapper.SiteApiMapper;
import ru.proxodnaya.people.api.mapper.WorkerApiMapper;
import ru.proxodnaya.people.features.device.model.Device;
import ru.proxodnaya.people.features.device.query.service.DeviceQueryService;
import ru.proxodnaya.people.features.site.model.Checkpoint;
import ru.proxodnaya.people.features.site.query.service.SiteQueryService;
import ru.proxodnaya.people.features.worker.query.service.WorkerQueryService;

/**
 * Реализация внутреннего API.
 */
@RestController
public class InternalControllerImpl implements InternalController {

    private final WorkerQueryService workers;
    private final DeviceQueryService devices;
    private final SiteQueryService sites;
    private final WorkerApiMapper workerMapper;
    private final SiteApiMapper siteMapper;

    public InternalControllerImpl(WorkerQueryService workers, DeviceQueryService devices, SiteQueryService sites,
                                  WorkerApiMapper workerMapper, SiteApiMapper siteMapper) {
        this.workers = workers;
        this.devices = devices;
        this.sites = sites;
        this.workerMapper = workerMapper;
        this.siteMapper = siteMapper;
    }

    @Override
    public AccessProfileRs accessProfile(UUID id) {
        WorkerRs worker = workerMapper.toRs(workers.get(id));
        return new AccessProfileRs(worker.status(), worker.fullName(), worker.position(), worker.contractor(),
                devices.byWorker(id).stream().filter(device -> Device.ACTIVE.equals(device.status()))
                        .map(Device::deviceId).toList(),
                worker.permits(), worker.zoneIds());
    }

    @Override
    public InternalCheckpointRs checkpoint(UUID id) {
        Checkpoint checkpoint = sites.checkpoint(id);
        CheckpointRs view = siteMapper.toRs(checkpoint);
        return new InternalCheckpointRs(view.id(), view.zoneId(), view.siteId(), view.name(), sites.zoneCapacity(checkpoint.zoneId()));
    }
}
