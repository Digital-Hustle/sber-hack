package ru.proxodnaya.people.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.people.api.controller.AuthController;
import ru.proxodnaya.people.api.dto.request.ActivateRq;
import ru.proxodnaya.people.api.dto.request.DeviceChallengeRq;
import ru.proxodnaya.people.api.dto.request.DeviceTokenRq;
import ru.proxodnaya.people.api.dto.response.ActivationRs;
import ru.proxodnaya.people.api.dto.response.DeviceChallengeRs;
import ru.proxodnaya.people.api.dto.response.TokenRs;
import ru.proxodnaya.people.api.mapper.DeviceApiMapper;
import ru.proxodnaya.people.features.device.command.service.DeviceAuthService;
import ru.proxodnaya.people.features.device.command.service.DeviceService;
import ru.proxodnaya.people.features.device.model.DeviceChallenge;
import ru.proxodnaya.people.features.token.api.TokenIssuer;

/**
 * Реализация входа рабочего.
 */
@RestController
public class AuthControllerImpl implements AuthController {

    private final DeviceService devices;
    private final DeviceAuthService deviceAuth;
    private final DeviceApiMapper mapper;

    public AuthControllerImpl(DeviceService devices, DeviceAuthService deviceAuth, DeviceApiMapper mapper) {
        this.devices = devices;
        this.deviceAuth = deviceAuth;
        this.mapper = mapper;
    }

    @Override
    public ActivationRs activate(ActivateRq request) {
        return mapper.toRs(devices.activate(request.inviteCode(), request.deviceId(), request.publicKeyJwk()));
    }

    @Override
    public DeviceChallengeRs deviceChallenge(DeviceChallengeRq request) {
        DeviceChallenge challenge = deviceAuth.challenge(request.deviceId());
        return new DeviceChallengeRs(challenge.challenge(), challenge.expiresAt());
    }

    @Override
    public TokenRs deviceToken(DeviceTokenRq request) {
        TokenIssuer.Issued issued = deviceAuth.token(request.deviceId(), request.challenge(), request.signature());
        return new TokenRs(issued.token(), "Bearer", issued.expiresAt());
    }
}
