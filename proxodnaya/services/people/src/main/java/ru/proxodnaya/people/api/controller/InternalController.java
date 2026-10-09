package ru.proxodnaya.people.api.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.people.api.constants.PathConstants;
import ru.proxodnaya.people.api.dto.response.AccessProfileRs;
import ru.proxodnaya.people.api.dto.response.InternalCheckpointRs;

/**
 * Внутренний API для access (PPL-08). Gateway наружу {@code /internal/**} не пускает; нужна роль сервиса.
 */
@Tag(name = "Internal", description = "Для других сервисов")
@RequestMapping(PathConstants.INTERNAL)
public interface InternalController {

    /**
     * Профиль доступа рабочего.
     *
     * @param id рабочий
     * @return статус, телефоны, допуски, зоны; 404 — рабочего нет
     */
    @Operation(summary = "Профиль доступа рабочего")
    @GetMapping(PathConstants.ACCESS_PROFILE)
    @PreAuthorize("hasRole('SVC_ACCESS')")
    AccessProfileRs accessProfile(@PathVariable UUID id);

    /**
     * Проходная: в какую зону ведёт и вместимость зоны (ACC-17).
     *
     * @param id проходная
     * @return проходная; 404 — нет
     */
    @Operation(summary = "Проходная для access")
    @GetMapping(PathConstants.INTERNAL_CHECKPOINT)
    @PreAuthorize("hasRole('SVC_ACCESS')")
    InternalCheckpointRs checkpoint(@PathVariable UUID id);
}
