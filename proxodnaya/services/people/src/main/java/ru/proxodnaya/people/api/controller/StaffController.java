package ru.proxodnaya.people.api.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.people.api.constants.PathConstants;
import ru.proxodnaya.people.api.dto.request.InviteStaffRq;
import ru.proxodnaya.people.api.dto.request.StaffRoleRq;
import ru.proxodnaya.people.api.dto.response.AccessLogPageRs;
import ru.proxodnaya.people.api.dto.response.StaffPageRs;
import ru.proxodnaya.people.api.dto.response.StaffUserRs;
import ru.proxodnaya.people.features.staff.model.StaffRole;
import ru.proxodnaya.people.features.staff.model.StaffStatus;

/**
 * Доступ и роли персонала (ADR-041): только ADMIN; журнал доступа — ещё SECURITY_OFFICER.
 */
@Tag(name = "Staff", description = "Персонал кабинета и журнал доступа")
@RequestMapping(PathConstants.STAFF)
public interface StaffController {

    /**
     * Список персонала.
     *
     * @param query  поиск по имени и почте
     * @param role   роль
     * @param status статус
     * @param cursor курсор
     * @param limit  размер (≤ 200)
     * @return страница
     */
    @Operation(summary = "Список персонала")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    StaffPageRs list(@RequestParam(name = "q", required = false) String query, @RequestParam(required = false) StaffRole role,
                     @RequestParam(required = false) StaffStatus status, @RequestParam(required = false) String cursor,
                     @RequestParam(required = false) Integer limit);

    /**
     * Приглашает сотрудника (письмо со ссылкой на вход).
     *
     * @param request имя, почта, роль
     * @param token   кто приглашает
     * @return сотрудник INVITED; 409 CONFLICT — почта занята
     */
    @Operation(summary = "Пригласить сотрудника")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    StaffUserRs invite(@Valid @RequestBody InviteStaffRq request, @Parameter(hidden = true) @AuthenticationPrincipal Jwt token);

    /**
     * Меняет роль.
     *
     * @param id      сотрудник
     * @param request роль
     * @param token   кто
     * @return сотрудник; 409 LAST_ADMIN
     */
    @Operation(summary = "Сменить роль")
    @PatchMapping(PathConstants.STAFF_ID)
    @PreAuthorize("hasRole('ADMIN')")
    StaffUserRs setRole(@PathVariable UUID id, @Valid @RequestBody StaffRoleRq request,
                        @Parameter(hidden = true) @AuthenticationPrincipal Jwt token);

    /**
     * Отключает сотрудника: сессии закрываются сразу.
     *
     * @param id    сотрудник
     * @param token кто
     * @return сотрудник; 409 LAST_ADMIN
     */
    @Operation(summary = "Отключить сотрудника")
    @PostMapping(PathConstants.STAFF_DISABLE)
    @PreAuthorize("hasRole('ADMIN')")
    StaffUserRs disable(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt token);

    /**
     * Включает сотрудника.
     *
     * @param id    сотрудник
     * @param token кто
     * @return сотрудник
     */
    @Operation(summary = "Включить сотрудника")
    @PostMapping(PathConstants.STAFF_ENABLE)
    @PreAuthorize("hasRole('ADMIN')")
    StaffUserRs enable(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt token);

    /**
     * Журнал доступа, новые сверху.
     *
     * @param cursor курсор
     * @param limit  размер
     * @return страница
     */
    @Operation(summary = "Журнал доступа")
    @GetMapping(PathConstants.ACCESS_LOG)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_OFFICER')")
    AccessLogPageRs accessLog(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit);
}
