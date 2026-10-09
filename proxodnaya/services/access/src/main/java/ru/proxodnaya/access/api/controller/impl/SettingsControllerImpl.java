package ru.proxodnaya.access.api.controller.impl;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.SettingsController;
import ru.proxodnaya.access.api.dto.request.SettingsRq;
import ru.proxodnaya.access.api.dto.response.SettingsRs;
import ru.proxodnaya.access.features.settings.command.service.SettingsService;
import ru.proxodnaya.access.features.settings.model.PassSettings;

/**
 * Реализация настроек прохода.
 */
@RestController
public class SettingsControllerImpl implements SettingsController {

    private final SettingsService settings;

    public SettingsControllerImpl(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public SettingsRs get() {
        return toRs(settings.current());
    }

    @Override
    public SettingsRs update(SettingsRq request, Jwt token) {
        String name = token.getClaimAsString("name");
        PassSettings next = new PassSettings(request.faceThreshold(), request.qrToleranceSec(), request.shiftGraceMin(),
                request.requireShift(), request.repeatScanCooldownSec(), request.presenceTtlHours(), request.tempLockFailures(),
                request.tempLockWindowSec(), new PassSettings.Terminal(request.terminalMode(), request.terminalScope()),
                new PassSettings.Offline(request.offlinePolicy(), request.offlineMaxHours(), request.offlineAfterExpiry(),
                        request.offlineCheckShift(), request.offlineUnknownDevice()), null, null);
        return toRs(settings.update(next, name == null ? token.getSubject() : name));
    }

    private static SettingsRs toRs(PassSettings value) {
        return new SettingsRs(value.faceThreshold(), value.qrToleranceSec(), value.shiftGraceMin(), value.requireShift(),
                value.repeatScanCooldownSec(), value.presenceTtlHours(), value.tempLockFailures(), value.tempLockWindowSec(),
                value.terminal().mode(), value.terminal().scope(), value.offline().policy(), value.offline().maxHours(),
                value.offline().afterExpiry(), value.offline().checkShift(), value.offline().unknownDevice(),
                value.updatedAt() == null ? null : value.updatedAt().toEpochMilli(), value.updatedBy());
    }
}
