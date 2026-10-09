package ru.proxodnaya.shift.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.shift.api.constants.PathConstants;
import ru.proxodnaya.shift.api.dto.request.ShiftTemplateRq;
import ru.proxodnaya.shift.api.dto.response.ShiftTemplateRs;
import ru.proxodnaya.shift.api.dto.response.ShiftTemplatesRs;

/**
 * Шаблоны смен («День», «Ночь»).
 */
@Tag(name = "Shifts", description = "Смены")
@RequestMapping(PathConstants.TEMPLATES)
public interface ShiftTemplateController {

    /**
     * Все шаблоны.
     *
     * @return шаблоны по названию
     */
    @Operation(summary = "Шаблоны смен")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    ShiftTemplatesRs list();

    /**
     * Создаёт шаблон; занятое название — 409.
     *
     * @param request шаблон
     * @return шаблон
     */
    @Operation(summary = "Создать шаблон смены")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ShiftTemplateRs create(@Valid @RequestBody ShiftTemplateRq request);
}
