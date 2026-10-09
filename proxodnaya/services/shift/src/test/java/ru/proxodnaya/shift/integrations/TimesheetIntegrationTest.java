package ru.proxodnaya.shift.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ru.proxodnaya.common.event.EventEnvelope;
import ru.proxodnaya.shift.config.AbstractIntegrationTest;
import ru.proxodnaya.shift.features.report.query.service.TimesheetXlsxWriter;

/**
 * {@code people.worker.v1} → проекция карточек; интервалы → табель XLSX.
 */
class TimesheetIntegrationTest extends AbstractIntegrationTest {

    private static final long WAIT_MS = 20_000;
    private static final long POLL_MS = 100;
    private static final String XLSX = "/api/v1/reports/timesheet.xlsx";

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Test
    void workerEventThenInterval_timesheetHasNameAndHours() throws Exception {
        // given
        String workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
        String contractor = "Подрядчик " + workerId;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workerId", workerId);
        payload.put("status", "ACTIVE");
        payload.put("fullName", "Табельный Тест");
        payload.put("position", "Сварщик");
        payload.put("contractor", contractor);
        send(workerId, "WorkerCreated", payload);
        await(workerId);
        LocalDate yesterday = LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow"))).minusDays(1);
        Instant entered = yesterday.atTime(9, 0).atZone(ZoneId.of("Europe/Moscow")).toInstant();
        jdbc.update("insert into presence_intervals (id, worker_id, zone_id, entered_at, exited_at, close_reason, enter_attempt_id) "
                        + "values (?, ?, 'zone-main', ?, ?, 'EXIT', ?)", UUID.randomUUID(), workerId, Timestamp.from(entered),
                Timestamp.from(entered.plusSeconds(6 * 3600)), UUID.randomUUID());

        // when
        byte[] file = mockMvc.perform(get(XLSX).with(staff()).param("from", yesterday.toString()).param("to", yesterday.toString())
                        .param("contractor", contractor.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("timesheet-")))
                .andReturn().getResponse().getContentAsByteArray();

        // then
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet table = book.getSheet("Табель");
            Row codes = table.getRow(TimesheetXlsxWriter.FIRST_DATA_ROW);
            assertThat(codes.getCell(1).getStringCellValue()).isEqualTo("Табельный Тест");
            assertThat(codes.getCell(TimesheetXlsxWriter.FIRST_DAY_COLUMN).getStringCellValue()).isEqualTo("Т");
            assertThat(table.getRow(TimesheetXlsxWriter.FIRST_DATA_ROW + 1).getCell(TimesheetXlsxWriter.FIRST_DAY_COLUMN)
                    .getNumericCellValue()).isEqualTo(6.0);
            assertThat(table.getRow(TimesheetXlsxWriter.FIRST_DATA_ROW + 2)).isNull();
        }
    }

    @Test
    void timesheet_rolesAndValidation() throws Exception {
        mockMvc.perform(get(XLSX).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUARD")))
                        .param("from", "2026-10-01").param("to", "2026-10-02"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(XLSX).with(staff()).param("from", "2026-10-01").param("to", "2026-12-31"))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor staff() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private void send(String workerId, String type, Map<String, Object> payload) throws Exception {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), type, clock.instant(), workerId, null, payload);
        kafka.send("people.worker.v1", workerId, objectMapper.writeValueAsString(envelope)).get();
    }

    private void await(String workerId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbc.queryForObject("select count(*) from workers where id = ? and full_name is not null", Integer.class,
                    workerId);
            if (count != null && count == 1) {
                return;
            }
            Thread.sleep(POLL_MS);
        }
        throw new AssertionError("карточка рабочего не появилась");
    }
}
