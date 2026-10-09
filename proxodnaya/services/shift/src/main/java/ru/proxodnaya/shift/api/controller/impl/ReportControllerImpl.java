package ru.proxodnaya.shift.api.controller.impl;

import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ru.proxodnaya.shift.api.controller.ReportController;
import ru.proxodnaya.shift.features.report.query.service.TimesheetService;
import ru.proxodnaya.shift.features.report.query.service.TimesheetXlsxWriter;

/**
 * Реализация API отчётов.
 */
@RestController
public class ReportControllerImpl implements ReportController {

    private final TimesheetService timesheets;
    private final TimesheetXlsxWriter writer;

    public ReportControllerImpl(TimesheetService timesheets, TimesheetXlsxWriter writer) {
        this.timesheets = timesheets;
        this.writer = writer;
    }

    @Override
    public ResponseEntity<byte[]> timesheet(LocalDate from, LocalDate until, String contractor) {
        byte[] file = writer.write(timesheets.build(from, until, contractor));
        String name = "timesheet-" + from + "_" + until + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(file);
    }
}
