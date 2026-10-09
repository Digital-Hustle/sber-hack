package ru.proxodnaya.people.api.controller.impl;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import ru.proxodnaya.people.config.properties.StaffCookieProperties;
import ru.proxodnaya.people.features.staff.command.service.StaffLoginService;
import ru.proxodnaya.people.features.staff.model.StaffSession;
import ru.proxodnaya.people.features.staff.query.service.StaffQueryService;

/**
 * Реализация входа персонала.
 */
@RestController
public class StaffAuthControllerImpl implements StaffAuthController {

    private static final String SID = "sid";
    private static final String EXPIRED = "Сессия завершена — войдите снова";

    private final StaffLoginService login;
    private final StaffQueryService staff;
    private final StaffApiMapper mapper;
    private final StaffCookieProperties cookies;
    private final Clock clock;

    public StaffAuthControllerImpl(StaffLoginService login, StaffQueryService staff, StaffApiMapper mapper,
                                   StaffCookieProperties cookies, Clock clock) {
        this.login = login;
        this.staff = staff;
        this.mapper = mapper;
        this.cookies = cookies;
        this.clock = clock;
    }

    @Override
    public CodeSentRs requestCode(StaffCodeRq request) {
        return mapper.toRs(login.requestCode(request.email()));
    }

    @Override
    public StaffSessionRs verify(StaffVerifyRq request, HttpServletResponse response) {
        StaffSession session = login.verify(request.email(), request.code());
        setCookie(response, session.refreshToken(),
                Duration.ofMillis(Math.max(0, session.sessionExpiresAt() - clock.millis())).toSeconds());
        return mapper.toRs(session);
    }

    @Override
    public StaffSessionRs refresh(StaffRefreshRq request, HttpServletRequest http, HttpServletResponse response) {
        String refresh = request == null ? null : request.refreshToken();
        if (refresh == null || refresh.isBlank()) {
            refresh = cookieToken(http);
        }
        try {
            if (refresh == null || refresh.isBlank()) {
                throw new DomainException(ErrorCode.SESSION_EXPIRED, EXPIRED);
            }
            StaffSession session = login.refresh(refresh);
            setCookie(response, session.refreshToken(),
                Duration.ofMillis(Math.max(0, session.sessionExpiresAt() - clock.millis())).toSeconds());
            return mapper.toRs(session);
        } catch (DomainException error) {
            setCookie(response, "", 0);
            throw error;
        }
    }

    @Override
    public StaffUserRs me(Jwt token) {
        return mapper.toRs(staff.me(StaffApiMapper.uuid(token.getClaimAsString(SID)), StaffApiMapper.uuid(token.getSubject())));
    }

    @Override
    public void logout(Jwt token, HttpServletResponse response) {
        setCookie(response, "", 0);
        UUID sessionId = StaffApiMapper.uuid(token.getClaimAsString(SID));
        UUID staffId = StaffApiMapper.uuid(token.getSubject());
        if (sessionId == null || staffId == null) {
            throw new DomainException(ErrorCode.SESSION_EXPIRED, EXPIRED);
        }
        login.logout(sessionId, staffId);
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] values = request.getCookies();
        if (values != null) {
            for (Cookie cookie : values) {
                if (cookies.name().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void setCookie(HttpServletResponse response, String value, long seconds) {
        ResponseCookie cookie = ResponseCookie.from(cookies.name(), value).httpOnly(true).secure(cookies.secure())
                .sameSite("Strict").path(cookies.path()).maxAge(Duration.ofSeconds(seconds)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
