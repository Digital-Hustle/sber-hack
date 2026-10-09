package ru.documents.review.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.documents.review.api.dto.response.ExpiringRs;
import ru.documents.review.api.dto.response.MetricsRs;

/**
 * Отчёты и метрики.
 */
@Tag(name = "reports")
@RequestMapping("/api/v1")
public interface ReportsController {

    /** Скоро истекают (B-04). */
    @Operation(summary = "Истекающие документы")
    @GetMapping("/reports/expiring")
    ExpiringRs expiring(@RequestParam(name = "days", required = false) Integer days);

    /** То же в XLSX (старший оператор). */
    @Operation(summary = "Истекающие документы в XLSX")
    @GetMapping("/reports/expiring/export")
    ResponseEntity<byte[]> expiringXlsx(@RequestParam(name = "days", required = false) Integer days);

    /** Очередь в цифрах (B-06). */
    @Operation(summary = "Метрики очереди")
    @GetMapping("/metrics")
    MetricsRs metrics();
}
