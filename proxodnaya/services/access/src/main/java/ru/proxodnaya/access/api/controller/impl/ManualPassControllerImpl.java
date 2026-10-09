package ru.proxodnaya.access.api.controller.impl;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.ManualPassController;
import ru.proxodnaya.access.api.dto.request.ManualPassRq;
import ru.proxodnaya.access.api.dto.response.DecisionRs;
import ru.proxodnaya.access.api.mapper.PassApiMapper;
import ru.proxodnaya.access.features.pass.application.ManualPassService;

/**
 * Реализация ручного прохода.
 */
@RestController
public class ManualPassControllerImpl implements ManualPassController {

    private final ManualPassService manual;
    private final PassApiMapper mapper;

    public ManualPassControllerImpl(ManualPassService manual, PassApiMapper mapper) {
        this.manual = manual;
        this.mapper = mapper;
    }

    @Override
    public DecisionRs pass(Jwt token, ManualPassRq request) {
        return mapper.convert(manual.pass(request.workerId(), request.checkpointId(), request.note(), token.getSubject()));
    }
}
