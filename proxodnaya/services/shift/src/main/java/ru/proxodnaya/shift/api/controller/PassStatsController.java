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
import ru.proxodnaya.shift.api.dto.response.LoadByHourRs;
import ru.proxodnaya.shift.api.dto.response.RefusalsRs;

/**
 * Статистика проходов по проекции попыток access.
 */
@Tag(name = "Analytics", description = "Аналитика")
@RequestMapping(PathConstants.ANALYTICS)
public interface PassStatsController {

    /**
     * Отказы на входе по причинам: системные и правомерные.
     *
     * @param from       начало, по умолчанию 7 дней назад
     * @param until      конец, не включая (параметр {@code to}), по умолчанию сейчас; период до 93 дней
     * @param checkpoint проходная
     * @param simulated  включать демо-историю симулятора
     * @return статистика
     */
    @Operation(summary = "Отказы по причинам")
    @GetMapping(PathConstants.REFUSALS)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    RefusalsRs refusals(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
                        @RequestParam(required = false) String checkpoint,
                        @RequestParam(defaultValue = "false") boolean simulated);

    /**
     * Нагрузка на проходные по дню недели и часу (heatmap).
     *
     * @param from       начало, по умолчанию 28 дней назад
     * @param until      конец, не включая (параметр {@code to}), по умолчанию сейчас; период до 93 дней
     * @param checkpoint проходная
     * @param simulated  включать демо-историю симулятора
     * @return клетки
     */
    @Operation(summary = "Нагрузка по часам")
    @GetMapping(PathConstants.LOAD_BY_HOUR)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SECURITY_OFFICER')")
    LoadByHourRs loadByHour(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
                            @RequestParam(required = false) String checkpoint,
                        @RequestParam(defaultValue = "false") boolean simulated);
}
