package ru.proxodnaya.people.api.controller.impl;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.api.controller.StaffAuthController;
import ru.proxodnaya.people.api.dto.request.StaffCodeRq;
import ru.proxodnaya.people.api.dto.request.StaffRefreshRq;
import ru.proxodnaya.people.api.dto.request.StaffVerifyRq;
import ru.proxodnaya.people.api.dto.response.CodeSentRs;
import ru.proxodnaya.people.api.dto.response.StaffSessionRs;
import ru.proxodnaya.people.api.dto.response.StaffUserRs;
import ru.proxodnaya.people.api.mapper.StaffApiMapper;
import ru.proxodnaya.people.features.staff.command.service.StaffLoginService;
import ru.proxodnaya.people.features.staff.query.service.StaffQueryService;

/**
 * Реализация входа персонала.
 */
@RestController
public class StaffAuthControllerImpl implements StaffAuthController {

    private static final String SID = "sid";

    private final StaffLoginService login;
    private final StaffQueryService staff;
    private final StaffApiMapper mapper;

    public StaffAuthControllerImpl(StaffLoginService login, StaffQueryService staff, StaffApiMapper mapper) {
        this.login = login;
        this.staff = staff;
        this.mapper = mapper;
    }

    @Override
    public CodeSentRs requestCode(StaffCodeRq request) {
        return mapper.toRs(login.requestCode(request.email()));
    }

    @Override
    public StaffSessionRs verify(StaffVerifyRq request) {
        return mapper.toRs(login.verify(request.email(), request.code()));
    }

    @Override
    public StaffSessionRs refresh(StaffRefreshRq request) {
        return mapper.toRs(login.refresh(request.refreshToken()));
    }

    @Override
    public StaffUserRs me(Jwt token) {
        return mapper.toRs(staff.me(StaffApiMapper.uuid(token.getClaimAsString(SID)), StaffApiMapper.uuid(token.getSubject())));
    }

    @Override
    public void logout(Jwt token) {
        UUID sessionId = StaffApiMapper.uuid(token.getClaimAsString(SID));
        UUID staffId = StaffApiMapper.uuid(token.getSubject());
        if (sessionId == null || staffId == null) {
            throw new DomainException(ErrorCode.SESSION_EXPIRED, "Сессия завершена — войдите снова");
        }
        login.logout(sessionId, staffId);
    }
}
