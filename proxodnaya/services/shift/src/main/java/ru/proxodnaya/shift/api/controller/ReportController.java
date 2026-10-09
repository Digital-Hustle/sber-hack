package ru.proxodnaya.shift.api.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.proxodnaya.shift.api.constants.PathConstants;

/**
 * Отчёты-файлы для бухгалтерии.
 */
@Tag(name = "Reports", description = "Отчёты")
@RequestMapping(PathConstants.REPORTS)
public interface ReportController {

    /** Тип содержимого XLSX. */
    String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * Табель за период (B-06): строки — люди, столбцы — дни, коды Т/Н, часы и итоги.
     *
     * @param from       первый день
     * @param until      последний день, включительно (параметр {@code to}); период до 62 дней
     * @param contractor подрядчик, без учёта регистра
     * @return файл XLSX
     */
    @Operation(summary = "Табель XLSX за период", responses = @ApiResponse(responseCode = "200",
            content = @Content(mediaType = XLSX, schema = @Schema(type = "string", format = "binary"))))
    @GetMapping(value = PathConstants.TIMESHEET, produces = XLSX)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    ResponseEntity<byte[]> timesheet(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until,
                                     @RequestParam(required = false) String contractor);
}
