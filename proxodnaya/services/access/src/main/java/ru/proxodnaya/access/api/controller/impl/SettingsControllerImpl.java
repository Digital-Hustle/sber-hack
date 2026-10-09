package ru.proxodnaya.access.api.controller.impl;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.SettingsController;
import ru.proxodnaya.access.api.dto.request.SettingsRq;
import ru.proxodnaya.access.api.dto.response.SettingsRs;
import ru.proxodnaya.access.api.mapper.SettingsApiMapper;
import ru.proxodnaya.access.features.settings.command.service.SettingsService;
import ru.proxodnaya.access.features.settings.model.PassSettings;

/**
 * Реализация настроек прохода.
 */
@RestController
public class SettingsControllerImpl implements SettingsController {

    private final SettingsService settings;
    private final SettingsApiMapper mapper;

    public SettingsControllerImpl(SettingsService settings, SettingsApiMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    @Override
    public SettingsRs get() {
        return mapper.convert(settings.current());
    }

    @Override
    public SettingsRs update(SettingsRq request, Jwt token) {
        String name = token.getClaimAsString("name");
        PassSettings next = new PassSettings(request.faceThreshold(), request.qrToleranceSec(), request.shiftGraceMin(),
                request.requireShift(), request.repeatScanCooldownSec(), request.presenceTtlHours(), request.tempLockFailures(),
                request.tempLockWindowSec(), new PassSettings.Terminal(request.terminalMode(), request.terminalScope()),
                new PassSettings.Offline(request.offlinePolicy(), request.offlineMaxHours(), request.offlineAfterExpiry(),
                        request.offlineCheckShift(), request.offlineUnknownDevice()), null, null);
        return mapper.convert(settings.update(next, name == null ? token.getSubject() : name));
    }
}
