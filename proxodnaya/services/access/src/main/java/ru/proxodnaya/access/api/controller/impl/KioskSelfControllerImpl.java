package ru.proxodnaya.access.api.controller.impl;

import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.access.api.controller.KioskSelfController;
import ru.proxodnaya.access.api.dto.request.RegisterKioskRq;
import ru.proxodnaya.access.api.dto.response.KioskCheckpointRs;
import ru.proxodnaya.access.api.dto.response.KioskMeRs;
import ru.proxodnaya.access.api.dto.response.RegisterKioskRs;
import ru.proxodnaya.access.api.mapper.KioskApiMapper;
import ru.proxodnaya.access.api.mapper.SettingsApiMapper;
import ru.proxodnaya.access.features.kiosk.api.KioskPrincipal;
import ru.proxodnaya.access.features.kiosk.command.service.KioskService;
import ru.proxodnaya.access.features.kiosk.gateway.CheckpointGateway;
import ru.proxodnaya.access.features.kiosk.model.Kiosk;
import ru.proxodnaya.access.features.settings.command.service.SettingsService;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;

/**
 * Реализация API терминала о себе.
 */
@RestController
public class KioskSelfControllerImpl implements KioskSelfController {

    private final KioskService kiosks;
    private final CheckpointGateway checkpoints;
    private final SettingsService settings;
    private final KioskApiMapper mapper;
    private final SettingsApiMapper settingsMapper;
    private final ObjectMapper objectMapper;

    public KioskSelfControllerImpl(KioskService kiosks, CheckpointGateway checkpoints, SettingsService settings, KioskApiMapper mapper,
                                   SettingsApiMapper settingsMapper, ObjectMapper objectMapper) {
        this.kiosks = kiosks;
        this.checkpoints = checkpoints;
        this.settings = settings;
        this.mapper = mapper;
        this.settingsMapper = settingsMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public RegisterKioskRs register(RegisterKioskRq request) {
        String jwk;
        try {
            jwk = objectMapper.writeValueAsString(request.publicKeyJwk());
        } catch (JsonProcessingException error) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Неверный ключ терминала");
        }
        Kiosk kiosk = kiosks.register(jwk);
        return new RegisterKioskRs(kiosk.id().toString(), kiosk.pairCode());
    }

    @Override
    public KioskMeRs me(KioskPrincipal principal) {
        Kiosk kiosk = kiosks.require(principal.kioskId());
        KioskCheckpointRs checkpoint = kiosk.paired()
                ? checkpoints.find(kiosk.checkpointId()).map(mapper::convert).orElse(null)
                : null;
        return new KioskMeRs(mapper.convert(kiosk), checkpoint, settingsMapper.convert(settings.current()));
    }
}
