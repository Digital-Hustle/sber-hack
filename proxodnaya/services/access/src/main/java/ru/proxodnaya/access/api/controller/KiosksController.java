package ru.proxodnaya.access.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.PairKioskRq;
import ru.proxodnaya.access.api.dto.request.UpdateKioskRq;
import ru.proxodnaya.access.api.dto.response.KioskListRs;
import ru.proxodnaya.access.api.dto.response.KioskRs;

/**
 * Терминалы в кабинете (ACC-12): список, сопряжение по коду, правка, отвязка.
 */
@Tag(name = "Kiosks", description = "Терминалы проходных в кабинете")
@RequestMapping(PathConstants.KIOSKS)
public interface KiosksController {

    /**
     * Терминалы: сопряжённые и ждущие сопряжения.
     *
     * @return список
     */
    @Operation(summary = "Терминалы")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_OFFICER', 'INSTALLER', 'MANAGER')")
    KioskListRs list();

    /**
     * Привязывает терминал по коду с его экрана.
     *
     * @param request код, название, проходная, логика
     * @param token   кто привязал
     * @return терминал
     */
    @Operation(summary = "Сопряжение терминала по коду")
    @PostMapping(PathConstants.PAIR)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTALLER')")
    KioskRs pair(@Valid @RequestBody PairKioskRq request, @Parameter(hidden = true) @AuthenticationPrincipal Jwt token);

    /**
     * Меняет название, проходную или логику терминала.
     *
     * @param id      терминал
     * @param request изменения
     * @return терминал
     */
    @Operation(summary = "Правка терминала")
    @PatchMapping(PathConstants.KIOSK_ID)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTALLER', 'SECURITY_OFFICER')")
    KioskRs update(@PathVariable UUID id, @Valid @RequestBody UpdateKioskRq request);

    /**
     * Отвязывает терминал: на его экране снова код сопряжения.
     *
     * @param id терминал
     */
    @Operation(summary = "Отвязать терминал")
    @PostMapping(PathConstants.UNPAIR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTALLER')")
    void unpair(@PathVariable UUID id);
}
