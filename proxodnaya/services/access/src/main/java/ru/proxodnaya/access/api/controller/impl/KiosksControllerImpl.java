package ru.proxodnaya.access.api.controller.impl;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.KiosksController;
import ru.proxodnaya.access.api.dto.request.PairKioskRq;
import ru.proxodnaya.access.api.dto.request.UpdateKioskRq;
import ru.proxodnaya.access.api.dto.response.KioskListRs;
import ru.proxodnaya.access.api.dto.response.KioskRs;
import ru.proxodnaya.access.api.mapper.KioskApiMapper;
import ru.proxodnaya.access.features.kiosk.command.service.KioskService;
import ru.proxodnaya.access.features.kiosk.model.KioskPatch;

/**
 * Реализация терминалов в кабинете.
 */
@RestController
public class KiosksControllerImpl implements KiosksController {

    private final KioskService kiosks;
    private final KioskApiMapper mapper;

    public KiosksControllerImpl(KioskService kiosks, KioskApiMapper mapper) {
        this.kiosks = kiosks;
        this.mapper = mapper;
    }

    @Override
    public KioskListRs list() {
        return new KioskListRs(kiosks.list().stream().map(mapper::convert).toList());
    }

    @Override
    public KioskRs pair(PairKioskRq request, Jwt token) {
        String name = token.getClaimAsString("name");
        KioskPatch patch = new KioskPatch(request.name().trim(), request.checkpointId(), request.mode(), request.offlinePolicy());
        return mapper.convert(kiosks.pair(request.pairCode(), patch, name == null ? token.getSubject() : name));
    }

    @Override
    public KioskRs update(UUID id, UpdateKioskRq request) {
        String name = request.name() == null ? null : request.name().trim();
        return mapper.convert(kiosks.update(id, new KioskPatch(name, request.checkpointId(), request.mode(), request.offlinePolicy())));
    }

    @Override
    public void unpair(UUID id) {
        kiosks.unpair(id);
    }
}
