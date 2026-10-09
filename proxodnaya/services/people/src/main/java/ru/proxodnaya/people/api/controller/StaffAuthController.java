package ru.proxodnaya.people.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ru.proxodnaya.people.api.constants.PathConstants;
import ru.proxodnaya.people.api.dto.request.StaffCodeRq;
import ru.proxodnaya.people.api.dto.request.StaffRefreshRq;
import ru.proxodnaya.people.api.dto.request.StaffVerifyRq;
import ru.proxodnaya.people.api.dto.response.CodeSentRs;
import ru.proxodnaya.people.api.dto.response.StaffSessionRs;
import ru.proxodnaya.people.api.dto.response.StaffUserRs;

/**
 * Вход персонала по коду из письма (PPL-13, ADR-041). code/verify/refresh — без токена; rate-limit — gateway (GW-05).
 */
@Tag(name = "Staff auth", description = "Вход персонала по коду из письма")
@RequestMapping(PathConstants.AUTH)
public interface StaffAuthController {

    /**
     * Отправляет 6-значный код. Ответ одинаковый для любого адреса.
     *
     * @param request почта
     * @return маска адреса и сроки; 429 LOGIN_RESEND_TOO_SOON
     */
    @Operation(summary = "Отправить код входа на почту")
    @PostMapping(PathConstants.STAFF_CODE)
    CodeSentRs requestCode(@Valid @RequestBody StaffCodeRq request);

    /**
     * Проверяет код и открывает сессию 12 ч.
     *
     * @param request  почта и код
     * @param response ставит httpOnly-cookie с refresh (PPL-14)
     * @return токены и сотрудник; 400 LOGIN_CODE_INVALID, 410 LOGIN_CODE_EXPIRED, 429 LOGIN_CODE_LOCKED
     */
    @Operation(summary = "Войти по коду из письма")
    @PostMapping(PathConstants.STAFF_VERIFY)
    StaffSessionRs verify(@Valid @RequestBody StaffVerifyRq request, @Parameter(hidden = true) HttpServletResponse response);

    /**
     * Продлевает access-токен (refresh одноразовый).
     *
     * @param request  refresh в теле или пустое тело — тогда refresh из cookie
     * @param http     запрос (cookie)
     * @param response новая cookie
     * @return новые токены; 401 SESSION_EXPIRED
     */
    @Operation(summary = "Продлить сессию (refresh в теле или в httpOnly-cookie)")
    @PostMapping(PathConstants.STAFF_REFRESH)
    StaffSessionRs refresh(@Valid @RequestBody(required = false) StaffRefreshRq request, @Parameter(hidden = true) HttpServletRequest http,
                           @Parameter(hidden = true) HttpServletResponse response);

    /**
     * Текущий сотрудник.
     *
     * @param token JWT сессии
     * @return сотрудник; 401 SESSION_EXPIRED — сессия закрыта или сотрудник отключён
     */
    @Operation(summary = "Текущий сотрудник")
    @GetMapping(PathConstants.STAFF_ME)
    StaffUserRs me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token);

    /**
     * Выход: закрывает сессию.
     *
     * @param token    JWT сессии
     * @param response стирает cookie
     */
    @Operation(summary = "Выйти")
    @PostMapping(PathConstants.STAFF_LOGOUT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @Parameter(hidden = true) HttpServletResponse response);
}
