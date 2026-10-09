package ru.proxodnaya.shift.units;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.shift.config.properties.ShiftProperties;
import ru.proxodnaya.shift.features.report.query.model.DayMark;
import ru.proxodnaya.shift.features.report.query.model.PlannedShift;
import ru.proxodnaya.shift.features.report.query.model.PresenceSpan;
import ru.proxodnaya.shift.features.report.query.model.Timesheet;
import ru.proxodnaya.shift.features.report.query.model.TimesheetPerson;
import ru.proxodnaya.shift.features.report.query.model.TimesheetRow;
import ru.proxodnaya.shift.features.report.query.repository.TimesheetRepository;
import ru.proxodnaya.shift.features.report.query.service.TimesheetService;
import ru.proxodnaya.shift.features.report.query.service.TimesheetXlsxWriter;

class TimesheetServiceTest {

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final ShiftProperties PROPS = new ShiftProperties(MSK, Duration.ofMinutes(60), Duration.ofHours(2),
            Duration.ofHours(16));
    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 7);
    private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");
    private static final String ALPHA = "ООО Альфа";

    private final TimesheetRepository repository = mock(TimesheetRepository.class);
    private final TimesheetService service = new TimesheetService(repository, PROPS, Clock.fixed(NOW, MSK));

    @Test
    void build_nightIntervalSplitByMidnight_absentOnPlannedDay() {
        // given: ночь с 5-го на 6-е 22:00–02:00 МСК; смена 7-го без прохода
        when(repository.spans(any(), any(), any(), any())).thenReturn(List.of(
                new PresenceSpan("w1", Instant.parse("2026-10-05T19:00:00Z"), Instant.parse("2026-10-05T23:00:00Z"), false)));
        when(repository.shifts(any(), any())).thenReturn(List.of(new PlannedShift("w1", Instant.parse("2026-10-07T05:00:00Z"))));
        when(repository.people(any())).thenReturn(List.of(new TimesheetPerson("w1", "Иванов И.И.", "Монтажник", ALPHA)));

        // when
        Timesheet sheet = service.build(FROM, TO, null);

        // then
        TimesheetRow row = sheet.rows().getFirst();
        assertThat(sheet.days()).hasSize(3);
        assertThat(row.days()).extracting(day -> day.mark()).containsExactly(DayMark.PRESENT, DayMark.PRESENT, DayMark.ABSENT);
        assertThat(row.days()).extracting(day -> day.minutes()).containsExactly(120L, 120L, 0L);
        assertThat(row.daysWorked()).isEqualTo(2);
        assertThat(row.absences()).isEqualTo(1);
        assertThat(row.minutes()).isEqualTo(240);
        assertThat(sheet.method()).contains("Europe/Moscow");
    }

    @Test
    void build_contractorFilter_unknownWorkerExcluded_incompleteMarked() {
        // given
        when(repository.spans(any(), any(), any(), any())).thenReturn(List.of(
                new PresenceSpan("w1", Instant.parse("2026-10-06T05:00:00Z"), Instant.parse("2026-10-06T14:00:00Z"), true),
                new PresenceSpan("w2", Instant.parse("2026-10-06T05:00:00Z"), Instant.parse("2026-10-06T06:00:00Z"), false),
                new PresenceSpan("ghost", Instant.parse("2026-10-06T05:00:00Z"), Instant.parse("2026-10-06T06:00:00Z"), false)));
        when(repository.shifts(any(), any())).thenReturn(List.of());
        when(repository.people(any())).thenReturn(List.of(new TimesheetPerson("w1", "Петров", null, ALPHA),
                new TimesheetPerson("w2", "Сидоров", null, "ООО Бета")));

        // when
        Timesheet filtered = service.build(FROM, TO, "ооо альфа");
        Timesheet all = service.build(FROM, TO, " ");

        // then
        assertThat(filtered.rows()).extracting(row -> row.person().workerId()).containsExactly("w1");
        assertThat(filtered.rows().getFirst().incomplete()).isEqualTo(1);
        assertThat(filtered.rows().getFirst().days().get(1).incomplete()).isTrue();
        assertThat(all.contractor()).isNull();
        assertThat(all.rows()).extracting(row -> row.person().fullName()).containsExactly("Петров", "Сидоров", "ghost");
    }

    @Test
    void build_badPeriod_validationError() {
        assertThatThrownBy(() -> service.build(TO, FROM, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.build(FROM, FROM.plusDays(62), null)).isInstanceOf(DomainException.class);
    }

    @Test
    void write_xlsx_codesHoursAndTotals() throws Exception {
        // given
        when(repository.spans(any(), any(), any(), any())).thenReturn(List.of(
                new PresenceSpan("w1", Instant.parse("2026-10-05T05:00:00Z"), Instant.parse("2026-10-05T13:30:00Z"), false)));
        when(repository.shifts(any(), any())).thenReturn(List.of());
        when(repository.people(any())).thenReturn(List.of(new TimesheetPerson("w1", "Иванов И.И.", "Монтажник", ALPHA)));

        // when
        byte[] file = new TimesheetXlsxWriter().write(service.build(FROM, TO, null));

        // then
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet table = book.getSheet("Табель");
            int first = TimesheetXlsxWriter.FIRST_DATA_ROW;
            int day = TimesheetXlsxWriter.FIRST_DAY_COLUMN;
            assertThat(table.getRow(first).getCell(1).getStringCellValue()).isEqualTo("Иванов И.И.");
            assertThat(table.getRow(first).getCell(day).getStringCellValue()).isEqualTo("Т");
            assertThat(table.getRow(first + 1).getCell(day).getNumericCellValue()).isEqualTo(8.5);
            assertThat(table.getRow(first).getCell(day + 3).getNumericCellValue()).isEqualTo(1);
            assertThat(table.getRow(first).getCell(day + 4).getNumericCellValue()).isEqualTo(8.5);
            assertThat(book.getSheet("Как посчитано").getRow(0).getCell(0).getStringCellValue()).contains("Т —");
        }
    }
}
