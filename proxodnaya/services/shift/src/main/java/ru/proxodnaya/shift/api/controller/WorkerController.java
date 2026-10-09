package ru.proxodnaya.shift.api.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.shift.api.constants.PathConstants;
import ru.proxodnaya.shift.api.dto.response.ShiftPageRs;
import ru.proxodnaya.shift.api.dto.response.WorkerMeRs;

/**
 * Телефон рабочего: только свои данные, рабочий — из токена ({@code sub}).
 */
@Tag(name = "Worker", description = "Телефон рабочего")
@RequestMapping(PathConstants.WORKER)
public interface WorkerController {

    /**
     * Мой статус: на объекте ли, зона, смена, отработано сегодня.
     *
     * @param token токен рабочего
     * @return статус
     */
    @Operation(summary = "Мой статус")
    @GetMapping(PathConstants.ME)
    @PreAuthorize("hasRole('WORKER')")
    WorkerMeRs me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token);

    /**
     * Мои смены.
     *
     * @param token  токен рабочего
     * @param from   начало периода
     * @param until  конец периода (параметр {@code to})
     * @param cursor {@code page.next}
     * @param limit  1..200
     * @return страница
     */
    @Operation(summary = "Мои смены")
    @GetMapping(PathConstants.MY_SHIFTS)
    @PreAuthorize("hasRole('WORKER')")
    ShiftPageRs shifts(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                       @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
                       @RequestParam(required = false) String cursor,
                       @RequestParam(required = false) Integer limit);
}
