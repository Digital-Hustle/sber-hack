package ru.proxodnaya.shift.api.controller.impl;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.AlertController;
import ru.proxodnaya.shift.api.dto.response.AlertListRs;
import ru.proxodnaya.shift.api.dto.response.AlertRs;
import ru.proxodnaya.shift.api.mapper.AlertApiMapper;
import ru.proxodnaya.shift.features.alert.command.service.AlertService;
import ru.proxodnaya.shift.features.alert.query.service.AlertQueryService;

/**
 * Реализация API оповещений.
 */
@RestController
public class AlertControllerImpl implements AlertController {

    private final AlertService alerts;
    private final AlertQueryService query;
    private final AlertApiMapper mapper;

    public AlertControllerImpl(AlertService alerts, AlertQueryService query, AlertApiMapper mapper) {
        this.alerts = alerts;
        this.query = query;
        this.mapper = mapper;
    }

    @Override
    public AlertListRs list(String status, int limit) {
        return mapper.convert(query.latest("open".equals(status), limit));
    }

    @Override
    public AlertRs ack(UUID id, Jwt token) {
        alerts.ack(id, token.getSubject());
        return mapper.convert(query.get(id));
    }
}
