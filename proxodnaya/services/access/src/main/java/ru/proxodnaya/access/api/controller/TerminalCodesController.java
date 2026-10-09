package ru.proxodnaya.access.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.SetTerminalCodeRq;
import ru.proxodnaya.access.api.dto.response.TerminalCodeInfoRs;
import ru.proxodnaya.access.api.dto.response.TerminalCodesRs;

/**
 * Настройка локальных кодов терминала (ACC-19).
 */
@Tag(name = "Terminal codes", description = "Коды терминалов")
@RequestMapping(PathConstants.TERMINAL_CODES)
public interface TerminalCodesController {

    /**
     * Состояние без проверочных хэшей.
     *
     * @return свойства кодов
     */
    @Operation(summary = "Состояние кодов терминала")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTALLER', 'SECURITY_OFFICER')")
    TerminalCodesRs get();

    /**
     * Меняет один код: инженер — сервисный, СБ — охранника, администратор — оба.
     *
     * @param kind    service или guard
     * @param request новый код
     * @param token   автор
     * @return свойства без соли и хэша
     */
    @Operation(summary = "Изменить код терминала")
    @PutMapping(PathConstants.CODE_KIND)
    @PreAuthorize("hasRole('ADMIN') or (#kind == 'service' and hasRole('INSTALLER')) "
            + "or (#kind == 'guard' and hasRole('SECURITY_OFFICER'))")
    TerminalCodeInfoRs update(@PathVariable String kind, @Valid @RequestBody SetTerminalCodeRq request,
                             @Parameter(hidden = true) @AuthenticationPrincipal Jwt token);
}
