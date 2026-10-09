package ru.proxodnaya.access.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.RegisterKioskRq;
import ru.proxodnaya.access.api.dto.response.KioskMeRs;
import ru.proxodnaya.access.api.dto.response.RegisterKioskRs;
import ru.proxodnaya.access.features.kiosk.api.KioskPrincipal;

/**
 * Терминал о себе (ACC-12): регистрация ключа и состояние. Кроме регистрации, запросы подписаны ключом терминала.
 */
@Tag(name = "Kiosk", description = "Скан QR, челлендж живости, решение о проходе")
@RequestMapping(PathConstants.KIOSK_SELF)
public interface KioskSelfController {

    /**
     * Первый запуск: регистрирует ключ терминала, выдаёт код сопряжения.
     *
     * @param request публичный ключ
     * @return id терминала и код
     */
    @Operation(summary = "Регистрация ключа терминала → код сопряжения")
    @PostMapping(PathConstants.REGISTER)
    @ResponseStatus(HttpStatus.CREATED)
    RegisterKioskRs register(@Valid @RequestBody RegisterKioskRq request);

    /**
     * Состояние терминала: привязан ли, к какой проходной, логика прохода. Заодно — пульс.
     *
     * @param kiosk терминал из подписи
     * @return терминал, проходная, настройки
     */
    @Operation(summary = "Состояние терминала (подпись X-Kiosk-*)")
    @GetMapping(PathConstants.ME)
    @PreAuthorize("hasRole('KIOSK')")
    KioskMeRs me(@Parameter(hidden = true) @AuthenticationPrincipal KioskPrincipal kiosk);
}
