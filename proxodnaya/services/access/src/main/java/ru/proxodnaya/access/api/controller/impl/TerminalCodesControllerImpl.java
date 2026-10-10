package ru.proxodnaya.access.api.controller.impl;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.access.api.controller.TerminalCodesController;
import ru.proxodnaya.access.api.dto.request.SetTerminalCodeRq;
import ru.proxodnaya.access.api.dto.response.TerminalCodeInfoRs;
import ru.proxodnaya.access.api.dto.response.TerminalCodesRs;
import ru.proxodnaya.access.api.mapper.TerminalCodeApiMapper;
import ru.proxodnaya.access.features.terminalcode.command.service.TerminalCodeService;

/**
 * Коды терминала; автор всегда берётся из JWT.
 */
@RestController
public class TerminalCodesControllerImpl implements TerminalCodesController {

    private final TerminalCodeService codes;
    private final TerminalCodeApiMapper mapper;

    public TerminalCodesControllerImpl(TerminalCodeService codes, TerminalCodeApiMapper mapper) {
        this.codes = codes;
        this.mapper = mapper;
    }

    @Override
    public TerminalCodesRs get() {
        return mapper.convert(codes.current());
    }

    @Override
    public TerminalCodeInfoRs update(String kind, SetTerminalCodeRq request, Jwt token) {
        String name = token.getClaimAsString("name");
        return mapper.convert(codes.update(kind, request.code(), name == null ? token.getSubject() : name));
    }
}
