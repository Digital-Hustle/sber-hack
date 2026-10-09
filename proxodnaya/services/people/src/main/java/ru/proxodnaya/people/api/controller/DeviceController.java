package ru.proxodnaya.people.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.people.api.constants.PathConstants;
import ru.proxodnaya.people.api.dto.response.DeviceListRs;
import ru.proxodnaya.people.api.dto.response.InviteRs;

/**
 * Инвайты и телефоны рабочего (кабинет).
 */
@Tag(name = "Devices", description = "Инвайты и привязанные телефоны")
@RequestMapping(PathConstants.WORKERS)
public interface DeviceController {

    /**
     * Новый инвайт (прежний неиспользованный отзывается).
     *
     * @param id рабочий
     * @return код и ссылка
     */
    @Operation(summary = "Выдать инвайт рабочему")
    @PostMapping(PathConstants.INVITE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    InviteRs invite(@PathVariable UUID id);

    /**
     * Телефоны рабочего.
     *
     * @param id рабочий
     * @return устройства
     */
    @Operation(summary = "Телефоны рабочего")
    @GetMapping(PathConstants.DEVICES)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_OFFICER')")
    DeviceListRs devices(@PathVariable UUID id);

    /**
     * Отвязать телефон.
     *
     * @param id       рабочий
     * @param deviceId устройство
     */
    @Operation(summary = "Отвязать телефон рабочего")
    @DeleteMapping(PathConstants.DEVICE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void revoke(@PathVariable UUID id, @PathVariable String deviceId);
}
