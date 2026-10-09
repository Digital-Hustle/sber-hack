package ru.proxodnaya.access.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.access.api.constants.PathConstants;
import ru.proxodnaya.access.api.dto.request.SettingsRq;
import ru.proxodnaya.access.api.dto.response.SettingsRs;

/**
 * Настройки прохода из кабинета (ACC-18). Применяются без перезапуска, до всех экземпляров — за 5 с.
 */
@Tag(name = "Settings", description = "Настройки прохода")
@RequestMapping(PathConstants.SETTINGS)
public interface SettingsController {

    /**
     * Текущие настройки.
     *
     * @return настройки
     */
    @Operation(summary = "Настройки прохода")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_OFFICER', 'MANAGER')")
    SettingsRs get();

    /**
     * Меняет настройки целиком.
     *
     * @param request все поля
     * @param token   кто меняет
     * @return сохранённые настройки; 400 VALIDATION_ERROR — значение вне диапазона
     */
    @Operation(summary = "Изменить настройки прохода")
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_OFFICER')")
    SettingsRs update(@Valid @RequestBody SettingsRq request, @Parameter(hidden = true) @AuthenticationPrincipal Jwt token);
}
