package ru.proxodnaya.shift.api.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.shift.api.constants.PathConstants;
import ru.proxodnaya.shift.api.dto.response.IntervalPageRs;

/**
 * Интервалы присутствия (FR-22/23).
 */
@Tag(name = "Intervals", description = "Интервалы присутствия")
@RequestMapping(PathConstants.INTERVALS)
public interface IntervalController {

    /**
     * Интервалы, новые сверху.
     *
     * @param from      вход не раньше, включительно
     * @param until     вход раньше (параметр {@code to})
     * @param worker    рабочий
     * @param zone      зона
     * @param simulated включать симулятор (по умолчанию нет)
     * @param cursor    {@code page.next}
     * @param limit     1..200, по умолчанию 50
     * @return страница
     */
    @Operation(summary = "Интервалы присутствия: фильтры и курсор")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    IntervalPageRs list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
                        @RequestParam(required = false) String worker,
                        @RequestParam(required = false) String zone,
                        @RequestParam(defaultValue = "false") boolean simulated,
                        @RequestParam(required = false) String cursor,
                        @RequestParam(required = false) Integer limit);
}
