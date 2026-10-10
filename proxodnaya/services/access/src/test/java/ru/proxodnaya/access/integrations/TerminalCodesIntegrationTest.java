package ru.proxodnaya.access.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.proxodnaya.access.config.AbstractIntegrationTest;

/**
 * ACC-19: роли, отказ слабых кодов, PBKDF2 и отсутствие секретов в API кабинета.
 */
class TerminalCodesIntegrationTest extends AbstractIntegrationTest {

    private static final String CODES = "/api/v1/terminal-codes";
    private static final String RAW = "482651";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearCodes() {
        jdbc.update("delete from terminal_codes");
    }

    @Test
    void empty_noFactoryCodes() throws Exception {
        // when / then
        mockMvc.perform(as(get(CODES), "INSTALLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").doesNotExist())
                .andExpect(jsonPath("$.guard").doesNotExist());
    }

    @Test
    void save_storedHashMatchesBrowserPbkdf_notReturnedToCabinet() throws Exception {
        // when
        mockMvc.perform(as(put(CODES + "/service"), "INSTALLER").content(body(RAW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("service"))
                .andExpect(jsonPath("$.digits").value(6))
                .andExpect(jsonPath("$.by").value("Инженер"))
                .andExpect(jsonPath("$.updatedAt").isNumber())
                .andExpect(jsonPath("$.hash").doesNotExist())
                .andExpect(jsonPath("$.salt").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());

        // then
        var stored = jdbc.queryForMap("select * from terminal_codes where kind = 'service'");
        byte[] salt = Base64.getUrlDecoder().decode((String) stored.get("salt"));
        assertThat(salt).hasSize(16);
        PBEKeySpec spec = new PBEKeySpec(RAW.toCharArray(), salt, 120_000, 256);
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        spec.clearPassword();
        assertThat(stored.get("hash")).isEqualTo(expected).isNotEqualTo(RAW);
        mockMvc.perform(as(get(CODES), "SECURITY_OFFICER"))
                .andExpect(jsonPath("$.service.digits").value(6))
                .andExpect(jsonPath("$.service.hash").doesNotExist())
                .andExpect(jsonPath("$.service.salt").doesNotExist());
    }

    @Test
    void saveSameCode_rotatesSalt_notAdditionalRows() throws Exception {
        // given
        mockMvc.perform(as(put(CODES + "/guard"), "ADMIN").content(body(RAW))).andExpect(status().isOk());
        String before = jdbc.queryForObject("select salt from terminal_codes where kind = 'guard'", String.class);

        // when
        mockMvc.perform(as(put(CODES + "/guard"), "SECURITY_OFFICER").content(body(RAW))).andExpect(status().isOk());

        // then
        assertThat(jdbc.queryForObject("select salt from terminal_codes where kind = 'guard'", String.class)).isNotEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from terminal_codes", Integer.class)).isEqualTo(1);
    }

    @Test
    void weakCodes_rejected_withoutSaving() throws Exception {
        // when / then
        for (String code : new String[]{"0000", "111111", "1234", "654321", "2026", "2580"}) {
            mockMvc.perform(as(put(CODES + "/service"), "ADMIN").content(body(code)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_WEAK"));
        }
        assertThat(jdbc.queryForObject("select count(*) from terminal_codes", Integer.class)).isZero();
        mockMvc.perform(as(put(CODES + "/unknown"), "ADMIN").content(body(RAW))).andExpect(status().isNotFound());
        mockMvc.perform(as(put(CODES + "/service"), "ADMIN").content(body("abc"))).andExpect(status().isBadRequest());
    }

    @Test
    void roleMatrix_preventsChangingOtherKind_andReadingWithoutStaffRole() throws Exception {
        // when / then
        mockMvc.perform(as(put(CODES + "/guard"), "INSTALLER").content(body(RAW))).andExpect(status().isForbidden());
        mockMvc.perform(as(put(CODES + "/service"), "SECURITY_OFFICER").content(body(RAW))).andExpect(status().isForbidden());
        for (String role : new String[]{"GUARD", "MANAGER", "WORKER", "KIOSK"}) {
            mockMvc.perform(as(get(CODES), role)).andExpect(status().isForbidden());
            mockMvc.perform(as(put(CODES + "/guard"), role).content(body(RAW))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(CODES)).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) {
        return request.with(jwt().jwt(token -> token.claim("name", "Инженер")).authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static String body(String value) {
        return "{\"code\":\"" + value + "\",\"by\":\"forged\"}";
    }
}
