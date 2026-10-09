package ru.proxodnaya.people.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.proxodnaya.people.api.constants.PathConstants;
import ru.proxodnaya.people.api.dto.request.CreateCheckpointRq;
import ru.proxodnaya.people.api.dto.request.CreateSiteRq;
import ru.proxodnaya.people.api.dto.request.CreateZoneRq;
import ru.proxodnaya.people.api.dto.response.CheckpointListRs;
import ru.proxodnaya.people.api.dto.response.CheckpointRs;
import ru.proxodnaya.people.api.dto.response.SiteListRs;
import ru.proxodnaya.people.api.dto.response.SiteRs;
import ru.proxodnaya.people.api.dto.response.ZoneListRs;
import ru.proxodnaya.people.api.dto.response.ZoneRs;

/**
 * Объекты, зоны, проходные (PPL-06). Читает персонал (охраннику нужен список проходных), меняет ADMIN.
 */
@Tag(name = "Sites", description = "Объекты, зоны и проходные")
public interface SiteController {

    /**
     * Объекты.
     *
     * @return объекты
     */
    @Operation(summary = "Объекты")
    @GetMapping(PathConstants.SITES)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER', 'GUARD')")
    SiteListRs sites();

    /**
     * Новый объект.
     *
     * @param request данные
     * @return объект
     */
    @Operation(summary = "Завести объект")
    @PostMapping(PathConstants.SITES)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    SiteRs createSite(@Valid @RequestBody CreateSiteRq request);

    /**
     * Зоны.
     *
     * @param siteId объект (необязательно)
     * @return зоны
     */
    @Operation(summary = "Зоны")
    @GetMapping(PathConstants.ZONES)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER', 'GUARD')")
    ZoneListRs zones(@RequestParam(required = false) UUID siteId);

    /**
     * Новая зона.
     *
     * @param request данные
     * @return зона
     */
    @Operation(summary = "Завести зону")
    @PostMapping(PathConstants.ZONES)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ZoneRs createZone(@Valid @RequestBody CreateZoneRq request);

    /**
     * Проходные.
     *
     * @param zoneId зона (необязательно)
     * @return проходные
     */
    @Operation(summary = "Проходные")
    @GetMapping(PathConstants.CHECKPOINTS)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER', 'GUARD')")
    CheckpointListRs checkpoints(@RequestParam(required = false) UUID zoneId);

    /**
     * Новая проходная.
     *
     * @param request данные
     * @return проходная
     */
    @Operation(summary = "Завести проходную")
    @PostMapping(PathConstants.CHECKPOINTS)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    CheckpointRs createCheckpoint(@Valid @RequestBody CreateCheckpointRq request);
}
