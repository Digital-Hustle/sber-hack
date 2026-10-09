package ru.proxodnaya.shift.api.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.shift.api.constants.PathConstants;
import ru.proxodnaya.shift.api.dto.response.ActiveShiftRs;

/**
 * Внутренний API для access (наружу gateway {@code /internal/**} не пропускает): токен сервиса с ролью SVC_ACCESS.
 */
@Tag(name = "Internal", description = "Для других сервисов")
@RequestMapping(PathConstants.INTERNAL_WORKERS)
public interface InternalShiftController {

    /**
     * Смена рабочего в момент прохода: в окне — {@code windowOk=true}; вне окна, но в этот день — {@code false};
     * смен в этот день нет — 404 (access отказывает {@code NO_SHIFT}).
     *
     * @param id     рабочий
     * @param moment момент прохода (параметр {@code at})
     * @return смена
     */
    @Operation(summary = "Активная смена рабочего (для access)")
    @GetMapping(PathConstants.ACTIVE_SHIFT)
    @PreAuthorize("hasRole('SVC_ACCESS')")
    ActiveShiftRs activeShift(@PathVariable String id,
                              @RequestParam(name = "at") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant moment);
}
