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
import ru.proxodnaya.shift.api.dto.response.HoursRs;
import ru.proxodnaya.shift.api.dto.response.SummaryRs;

/**
 * Аналитика по реальным интервалам и проходам; в каждом ответе {@code method} — как посчитано.
 */
@Tag(name = "Analytics", description = "Аналитика")
@RequestMapping(PathConstants.ANALYTICS)
public interface AnalyticsController {

    /**
     * Кто на объекте сейчас, по зонам, и проходы за сегодня.
     *
     * @return сводка
     */
    @Operation(summary = "Сейчас на объекте")
    @GetMapping(PathConstants.SUMMARY)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    SummaryRs summary();

    /**
     * Отработанные часы по рабочим.
     *
     * @param from   начало, включительно
     * @param until  конец, не включая (параметр {@code to}); период до 93 дней
     * @param worker рабочий
     * @param zone   зона
     * @return отчёт
     */
    @Operation(summary = "Отработанные часы за период")
    @GetMapping(PathConstants.HOURS)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    HoursRs hours(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                  @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
                  @RequestParam(required = false) String worker,
                  @RequestParam(required = false) String zone);
}
