package ru.proxodnaya.access.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.proxodnaya.access.config.AbstractIntegrationTest;
import ru.proxodnaya.access.features.settings.command.service.SettingsService;

/**
 * Настройки прохода (ACC-18): дефолты, сохранение, валидация, роли.
 */
class SettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String SETTINGS = "/api/v1/settings";
    private static final String BODY = """
            {"faceThreshold":0.5,"qrToleranceSec":60,"shiftGraceMin":30,"requireShift":false,"repeatScanCooldownSec":15,
             "presenceTtlHours":12,"tempLockFailures":4,"tempLockWindowSec":300,"terminalMode":"QR_FACE","terminalScope":"GLOBAL",
             "offlinePolicy":"GUARD","offlineMaxHours":8,"offlineAfterExpiry":"CLOSED","offlineCheckShift":true,
             "offlineUnknownDevice":"DENY"}""";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SettingsService settings;

    @AfterEach
    void restoreDefaults() {
        jdbc.update("delete from access_settings");
        settings.evict();
    }

    @Test
    void get_noSavedSettings_defaultsFromEnv() throws Exception {
        // when / then
        mockMvc.perform(as(get(SETTINGS), "ROLE_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToleranceSec").value(90))
                .andExpect(jsonPath("$.requireShift").value(true))
                .andExpect(jsonPath("$.tempLockFailures").value(5))
                .andExpect(jsonPath("$.offlineUnknownDevice").value("DENY"))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void put_validBody_savedAndApplied() throws Exception {
        // when
        mockMvc.perform(as(put(SETTINGS), "ROLE_SECURITY_OFFICER").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faceThreshold").value(0.5))
                .andExpect(jsonPath("$.updatedBy").value("Начальник СБ"))
                .andExpect(jsonPath("$.updatedAt").isNumber());

        // then
        mockMvc.perform(as(get(SETTINGS), "ROLE_ADMIN"))
                .andExpect(jsonPath("$.requireShift").value(false))
                .andExpect(jsonPath("$.offlineMaxHours").value(8));
        assertThat(settings.faceThreshold()).isEqualTo(0.5);
        assertThat(settings.tempLockFailures()).isEqualTo(4);
    }

    @Test
    void put_outOfRange_badRequest() throws Exception {
        // when / then
        mockMvc.perform(as(put(SETTINGS), "ROLE_ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"faceThreshold\":0.5", "\"faceThreshold\":0.95")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(put(SETTINGS), "ROLE_ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"offlineUnknownDevice\":\"DENY\"", "\"offlineUnknownDevice\":\"ALLOW\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_managerOrGuard_forbidden() throws Exception {
        // when / then
        mockMvc.perform(as(put(SETTINGS), "ROLE_MANAGER").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get(SETTINGS), "ROLE_GUARD")).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) {
        return request.with(jwt().jwt(token -> token.claim("name", "Начальник СБ")).authorities(new SimpleGrantedAuthority(role)));
    }
}
