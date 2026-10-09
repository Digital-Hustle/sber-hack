package ru.proxodnaya.people.api.controller.impl;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.people.api.controller.StaffController;
import ru.proxodnaya.people.api.dto.request.InviteStaffRq;
import ru.proxodnaya.people.api.dto.request.StaffRoleRq;
import ru.proxodnaya.people.api.dto.response.AccessLogPageRs;
import ru.proxodnaya.people.api.dto.response.StaffPageRs;
import ru.proxodnaya.people.api.dto.response.StaffUserRs;
import ru.proxodnaya.people.api.mapper.StaffApiMapper;
import ru.proxodnaya.people.features.staff.command.service.StaffCommandService;
import ru.proxodnaya.people.features.staff.model.StaffFilter;
import ru.proxodnaya.people.features.staff.model.StaffRole;
import ru.proxodnaya.people.features.staff.model.StaffStatus;
import ru.proxodnaya.people.features.staff.query.service.StaffQueryService;

/**
 * Реализация управления персоналом.
 */
@RestController
public class StaffControllerImpl implements StaffController {

    private final StaffCommandService commands;
    private final StaffQueryService queries;
    private final StaffApiMapper mapper;

    public StaffControllerImpl(StaffCommandService commands, StaffQueryService queries, StaffApiMapper mapper) {
        this.commands = commands;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override
    public StaffPageRs list(String query, StaffRole role, StaffStatus status, String cursor, Integer limit) {
        return mapper.toStaffPage(queries.page(new StaffFilter(query, role, status), cursor, limit));
    }

    @Override
    public StaffUserRs invite(InviteStaffRq request, Jwt token) {
        return mapper.toRs(commands.invite(mapper.actor(token), request.name(), request.email(), request.role()));
    }

    @Override
    public StaffUserRs setRole(UUID id, StaffRoleRq request, Jwt token) {
        return mapper.toRs(commands.setRole(mapper.actor(token), id, request.role()));
    }

    @Override
    public StaffUserRs disable(UUID id, Jwt token) {
        return mapper.toRs(commands.setActive(mapper.actor(token), id, false));
    }

    @Override
    public StaffUserRs enable(UUID id, Jwt token) {
        return mapper.toRs(commands.setActive(mapper.actor(token), id, true));
    }

    @Override
    public AccessLogPageRs accessLog(String cursor, Integer limit) {
        return mapper.toLogPage(queries.log(cursor, limit));
    }
}
