package ru.proxodnaya.shift.api.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.shift.api.constants.PathConstants;
import ru.proxodnaya.shift.api.dto.request.BulkShiftRq;
import ru.proxodnaya.shift.api.dto.request.ShiftRq;
import ru.proxodnaya.shift.api.dto.response.BulkShiftRs;
import ru.proxodnaya.shift.api.dto.response.ShiftPageRs;
import ru.proxodnaya.shift.api.dto.response.ShiftRs;

/**
 * Плановые смены (FR-20): читают ADMIN/MANAGER/SECURITY_OFFICER, назначает ADMIN.
 */
@Tag(name = "Shifts", description = "Смены")
@RequestMapping(PathConstants.SHIFTS)
public interface ShiftController {

    /**
     * Смены по началу, ранние сверху.
     *
     * @param from   начало периода по началу смены, включительно
     * @param until  конец периода, не включая (параметр {@code to})
     * @param worker рабочий
     * @param cursor {@code page.next}
     * @param limit  1..200, по умолчанию 50
     * @return страница
     */
    @Operation(summary = "Смены: фильтры и курсор")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    ShiftPageRs list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                     @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
                     @RequestParam(required = false) String worker,
                     @RequestParam(required = false) String cursor,
                     @RequestParam(required = false) Integer limit);

    /**
     * Назначает смену; пересечение с уже назначенной — 409.
     *
     * @param token   кто назначает
     * @param request смена
     * @return смена
     */
    @Operation(summary = "Назначить смену")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ShiftRs create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @Valid @RequestBody ShiftRq request);

    /**
     * Назначает смены бригаде по недельному графику; пересекающиеся с назначенными пропускает.
     *
     * @param token   кто назначает
     * @param request график
     * @return сколько создано и пропущено
     */
    @Operation(summary = "Массовое назначение смен")
    @PostMapping(PathConstants.BULK)
    @PreAuthorize("hasRole('ADMIN')")
    BulkShiftRs bulk(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, @Valid @RequestBody BulkShiftRq request);

    /**
     * Удаляет смену.
     *
     * @param id смена
     */
    @Operation(summary = "Удалить смену")
    @DeleteMapping(PathConstants.BY_ID)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void delete(@PathVariable UUID id);
}
