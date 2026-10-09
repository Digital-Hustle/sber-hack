package ru.proxodnaya.people.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.people.api.controller.AuthController;
import ru.proxodnaya.people.api.dto.request.ActivateRq;
import ru.proxodnaya.people.api.dto.response.ActivationRs;
import ru.proxodnaya.people.api.mapper.DeviceApiMapper;
import ru.proxodnaya.people.features.device.command.service.DeviceService;

/**
 * Реализация входа рабочего.
 */
@RestController
public class AuthControllerImpl implements AuthController {

    private final DeviceService devices;
    private final DeviceApiMapper mapper;

    public AuthControllerImpl(DeviceService devices, DeviceApiMapper mapper) {
        this.devices = devices;
        this.mapper = mapper;
    }

    @Override
    public ActivationRs activate(ActivateRq request) {
        return mapper.toRs(devices.activate(request.inviteCode(), request.deviceId(), request.publicKeyJwk()));
    }
}
