package ru.proxodnaya.access.api.controller.impl;

import java.time.Clock;
import java.util.UUID;

import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.KioskController;
import ru.proxodnaya.access.api.dto.request.FramesRq;
import ru.proxodnaya.access.api.dto.request.ScanRq;
import ru.proxodnaya.access.api.dto.response.DecisionRs;
import ru.proxodnaya.access.api.dto.response.ScanRs;
import ru.proxodnaya.access.api.mapper.PassApiMapper;
import ru.proxodnaya.access.features.kiosk.api.KioskPrincipal;
import ru.proxodnaya.access.features.kiosk.command.service.KioskService;
import ru.proxodnaya.access.features.pass.application.PassService;

/**
 * Реализация API киоска.
 */
@RestController
public class KioskControllerImpl implements KioskController {

    private final PassService passService;
    private final PassApiMapper mapper;
    private final KioskService kiosks;
    private final Clock clock;

    public KioskControllerImpl(PassService passService, PassApiMapper mapper, KioskService kiosks, Clock clock) {
        this.passService = passService;
        this.mapper = mapper;
        this.kiosks = kiosks;
        this.clock = clock;
    }

    @Override
    public ScanRs scan(ScanRq request, Object principal) {
        // проходная терминала — из его записи, а не из тела (ACC-12); охранник с токеном указывает её сам
        String checkpointId = principal instanceof KioskPrincipal kiosk ? kiosks.checkpointOf(kiosk.kioskId()) : request.checkpointId();
        return mapper.convert(passService.scan(request.qr(), checkpointId), clock.instant());
    }

    @Override
    public DecisionRs submitFrames(UUID token, FramesRq request) {
        return mapper.convert(passService.submitFrames(token, request.frames()));
    }
}
