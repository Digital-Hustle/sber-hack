package ru.proxodnaya.shift.integrations;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.proxodnaya.shift.config.AbstractIntegrationTest;

/**
 * Смены, шаблоны и внутренний API для access через HTTP и PostgreSQL.
 */
class ShiftApiIntegrationTest extends AbstractIntegrationTest {

    private static final String SHIFTS = "/api/v1/shifts";
    private static final String TEMPLATES = "/api/v1/shift-templates";
    private static final String ACTIVE = "/internal/shift/workers/{id}/active-shift";

    private String workerId;

    @BeforeEach
    void setUp() {
        workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void create_thenOverlap_201_409() throws Exception {
        // when / then
        mockMvc.perform(as("ADMIN", post(SHIFTS)).content(shift("2026-11-02T05:00:00Z", "2026-11-02T17:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workerId").value(workerId))
                .andExpect(jsonPath("$.id").isNotEmpty());
        mockMvc.perform(as("ADMIN", post(SHIFTS)).content(shift("2026-11-02T16:00:00Z", "2026-11-02T20:00:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void create_invalid_400_401_403() throws Exception {
        // when / then
        mockMvc.perform(as("ADMIN", post(SHIFTS)).content("{\"workerId\":\"" + workerId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
        mockMvc.perform(as("ADMIN", post(SHIFTS)).content(shift("2026-11-02T17:00:00Z", "2026-11-02T05:00:00Z")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(SHIFTS).contentType(MediaType.APPLICATION_JSON).content(shift("2026-11-02T05:00:00Z", "2026-11-02T17:00:00Z")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as("MANAGER", post(SHIFTS)).content(shift("2026-11-02T05:00:00Z", "2026-11-02T17:00:00Z")))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulk_nightShifts_thenListPagedAndDelete() throws Exception {
        // given: пн–ср ночью 20:00–08:00 МСК
        String body = "{\"workerIds\":[\"" + workerId + "\"],\"from\":\"2026-11-09\",\"to\":\"2026-11-15\",\"days\":["
                + "{\"weekday\":0,\"start\":\"20:00\",\"end\":\"08:00\"},{\"weekday\":1,\"start\":\"20:00\",\"end\":\"08:00\"},"
                + "{\"weekday\":2,\"start\":\"20:00\",\"end\":\"08:00\"}]}";

        // when / then
        mockMvc.perform(as("ADMIN", post(SHIFTS + "/bulk")).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.skipped").value(0));
        mockMvc.perform(as("ADMIN", post(SHIFTS + "/bulk")).content(body))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(3));
        String page = mockMvc.perform(as("MANAGER", get(SHIFTS)).param("worker", workerId).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].start").value("2026-11-09T17:00:00Z"))
                .andExpect(jsonPath("$.items[0].end").value("2026-11-10T05:00:00Z"))
                .andExpect(jsonPath("$.page.total").value(3))
                .andReturn().getResponse().getContentAsString();
        String next = objectMapper.readTree(page).path("page").path("next").asText();
        String last = mockMvc.perform(as("MANAGER", get(SHIFTS)).param("worker", workerId).param("cursor", next))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].start").value("2026-11-11T17:00:00Z"))
                .andExpect(jsonPath("$.page.next").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(last).path("items").get(0).path("id").asText();
        mockMvc.perform(as("ADMIN", delete(SHIFTS + "/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(as("ADMIN", delete(SHIFTS + "/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void list_badCursorOrLimit_400() throws Exception {
        // when / then
        mockMvc.perform(as("ADMIN", get(SHIFTS)).param("cursor", "garbage")).andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", get(SHIFTS)).param("limit", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", get(SHIFTS)).param("from", "not-a-date")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void templates_createListDuplicate() throws Exception {
        // given
        String name = "Ночь " + workerId;
        String body = "{\"name\":\"" + name + "\",\"start\":\"20:00\",\"end\":\"08:00\"}";

        // when / then
        mockMvc.perform(as("ADMIN", post(TEMPLATES)).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name));
        mockMvc.perform(as("ADMIN", post(TEMPLATES)).content(body)).andExpect(status().isConflict());
        mockMvc.perform(as("MANAGER", get(TEMPLATES))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(as("WORKER", get(TEMPLATES))).andExpect(status().isForbidden());
    }

    @Test
    void activeShift_forAccess_windowOkOutsideWindowOrNone() throws Exception {
        // given: смена 08:00–20:00 МСК, вход разрешён с 07:00
        mockMvc.perform(as("ADMIN", post(SHIFTS)).content(shift("2026-11-03T05:00:00Z", "2026-11-03T17:00:00Z")))
                .andExpect(status().isCreated());

        // when / then
        mockMvc.perform(as("SVC_ACCESS", get(ACTIVE, workerId)).param("at", "2026-11-03T04:10:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowOk").value(true));
        mockMvc.perform(as("SVC_ACCESS", get(ACTIVE, workerId)).param("at", "2026-11-03T03:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowOk").value(false));
        mockMvc.perform(as("SVC_ACCESS", get(ACTIVE, workerId)).param("at", "2026-11-05T05:00:00Z"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as("ADMIN", get(ACTIVE, workerId)).param("at", "2026-11-03T05:00:00Z"))
                .andExpect(status().isForbidden());
    }

    private String shift(String start, String end) {
        return "{\"workerId\":\"" + workerId + "\",\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject("user-" + role)).authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
