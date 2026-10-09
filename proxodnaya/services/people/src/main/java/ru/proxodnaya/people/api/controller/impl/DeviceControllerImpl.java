package ru.proxodnaya.people.api.controller.impl;

import java.util.UUID;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.people.api.controller.DeviceController;
import ru.proxodnaya.people.api.dto.response.DeviceListRs;
import ru.proxodnaya.people.api.dto.response.InviteRs;
import ru.proxodnaya.people.api.mapper.DeviceApiMapper;
import ru.proxodnaya.people.features.device.command.service.DeviceService;
import ru.proxodnaya.people.features.device.command.service.InviteService;
import ru.proxodnaya.people.features.device.query.service.DeviceQueryService;
import ru.proxodnaya.people.features.worker.query.service.WorkerQueryService;

/**
 * Реализация API инвайтов и телефонов.
 */
@RestController
public class DeviceControllerImpl implements DeviceController {

    private final InviteService invites;
    private final DeviceService devices;
    private final DeviceQueryService deviceQueries;
    private final WorkerQueryService workerQueries;
    private final DeviceApiMapper mapper;

    public DeviceControllerImpl(InviteService invites, DeviceService devices, DeviceQueryService deviceQueries,
                                WorkerQueryService workerQueries, DeviceApiMapper mapper) {
        this.invites = invites;
        this.devices = devices;
        this.deviceQueries = deviceQueries;
        this.workerQueries = workerQueries;
        this.mapper = mapper;
    }

    @Override
    public InviteRs invite(UUID id) {
        return mapper.toRs(invites.issue(id));
    }

    @Override
    public DeviceListRs devices(UUID id) {
        workerQueries.get(id);
        return mapper.toRs(deviceQueries.byWorker(id));
    }

    @Override
    public void revoke(UUID id, String deviceId) {
        devices.revoke(id, deviceId);
    }
}
