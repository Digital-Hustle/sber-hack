package ru.proxodnaya.access.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.access.api.dto.response.SettingsRs;

@Tag(name = "Settings", description = "Настройки прохода")
@RequestMapping("/api/v1/kiosk/settings")
public interface SettingsController {

    @Operation(summary = "Получить настройки")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    SettingsRs get();

    @Operation(summary = "Обновить настройки")
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    SettingsRs update(@RequestBody SettingsRs settings);
}