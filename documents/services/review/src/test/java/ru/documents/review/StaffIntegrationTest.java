package ru.documents.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.documents.review.features.staff.gateway.KeycloakAdminGateway;
import ru.documents.review.features.staff.gateway.StaffMailer;
import ru.documents.review.features.staff.service.Emails;
import ru.documents.review.features.staff.service.Totp;

/** FE-UI-04: вход по коду из письма; сотрудников приглашает и отключает только старший. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffIntegrationTest extends AbstractIntegrationTest {

    private static final String STAFF = "/api/v1/staff";
    private static final String CODE = "/api/v1/auth/code";
    private static final String MAIL = "ivanov@company.ru";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @MockitoBean
    private KeycloakAdminGateway keycloak;
    @MockitoBean
    private StaffMailer mailer;

    @Test
    void onlySeniorManagesStaff() throws Exception {
        mvc.perform(get(STAFF).with(as("op", "ROLE_OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get(STAFF).with(as("aud", "ROLE_AUDITOR"))).andExpect(status().isForbidden());
        mvc.perform(get(STAFF)).andExpect(status().isUnauthorized());
        mvc.perform(post(STAFF).with(as("op", "ROLE_OPERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content(body(MAIL, "OPERATOR"))).andExpect(status().isForbidden());
        verify(keycloak, never()).create(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void seniorInvitesByEmail() throws Exception {
        mvc.perform(post(STAFF).with(senior()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(" Ivanov@Company.ru ", "SENIOR_OPERATOR")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.login").value(MAIL))
                .andExpect(jsonPath("$.email").value(MAIL)).andExpect(jsonPath("$.role").value("SENIOR_OPERATOR"));
        verify(keycloak).create(eq(MAIL), eq("Иван"), eq("Иванов"), eq(MAIL), eq(List.of("OPERATOR", "SENIOR_OPERATOR")));
        verify(mailer).invitation(eq(MAIL), eq("Иван"), eq("Старший оператор"));
    }

    @Test
    void badOrTakenEmailIsRejected() throws Exception {
        mvc.perform(post(STAFF).with(senior()).contentType(MediaType.APPLICATION_JSON).content(body("и в", "OPERATOR")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        when(keycloak.findByEmail(MAIL)).thenReturn(Optional.of(user(true)));
        mvc.perform(post(STAFF).with(senior()).contentType(MediaType.APPLICATION_JSON).content(body(MAIL, "OPERATOR")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void seniorCannotDisableHimself() throws Exception {
        mvc.perform(put(STAFF + "/boss/enabled").with(senior()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_DISABLE"));
    }

    @Test
    void codeIsMailedWithoutJwt() throws Exception {
        when(keycloak.findByEmail(MAIL)).thenReturn(Optional.of(user(true)));
        when(keycloak.realmRoles("k1")).thenReturn(List.of("OPERATOR"));
        when(keycloak.codeIssuedAt("k1")).thenReturn(Optional.empty());
        mvc.perform(post(CODE).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"IVANOV@company.ru\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sentTo").value("iv••••@company.ru"))
                .andExpect(jsonPath("$.expiresInSec").value(600)).andExpect(jsonPath("$.resendInSec").value(45));
        verify(keycloak).issueCode(eq("k1"), anyString(), eq(600L));
        verify(mailer).loginCode(eq(MAIL), matches("\\d{6}"), eq(10L));
    }

    @Test
    void sameAnswerForStrangerDisabledAndTooOften() throws Exception {
        String other = "{\"email\":\"nobody@company.ru\"}";
        mvc.perform(post(CODE).contentType(MediaType.APPLICATION_JSON).content(other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sentTo").value("no••••@company.ru"));
        when(keycloak.findByEmail(MAIL)).thenReturn(Optional.of(user(false)));
        mvc.perform(post(CODE).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + MAIL + "\"}"))
                .andExpect(status().isOk());
        when(keycloak.findByEmail(MAIL)).thenReturn(Optional.of(user(true)));
        when(keycloak.realmRoles("k1")).thenReturn(List.of("AUDITOR"));
        when(keycloak.codeIssuedAt("k1")).thenReturn(Optional.of(Instant.now().minusSeconds(10)));
        mvc.perform(post(CODE).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + MAIL + "\"}"))
                .andExpect(status().isOk());
        verify(keycloak, never()).issueCode(anyString(), anyString(), anyLong());
        verify(mailer, never()).loginCode(anyString(), anyString(), anyLong());
        mvc.perform(post(CODE).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"не почта\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLoginIsOffInProd() throws Exception {
        mvc.perform(get("/api/v1/auth/mode")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("prod"))
                .andExpect(jsonPath("$.testUsers").isEmpty());
        mvc.perform(post("/api/v1/auth/test-code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + MAIL + "\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEST_MODE_OFF"));
        verify(keycloak, never()).issueCode(anyString(), anyString(), anyLong());
    }

    @Test
    void totpMatchesRfc6238() {
        assertThat(Totp.code("12345678901234567890", 59, 30)).isEqualTo("287082");
        assertThat(Totp.code("12345678901234567890", 1_111_111_109, 30)).isEqualTo("081804");
        assertThat(Totp.newSecret()).hasSize(32).matches("[A-Za-z0-9]+");
        assertThat(Emails.mask("a@b.ru")).isEqualTo("a•@b.ru");
    }

    private com.fasterxml.jackson.databind.JsonNode user(boolean enabled) {
        return json.valueToTree(Map.of("id", "k1", "username", "ivanov", "email", MAIL, "enabled", enabled));
    }

    private static String body(String email, String role) {
        return "{\"email\":\"" + email + "\",\"firstName\":\"Иван\",\"lastName\":\"Иванов\",\"role\":\"" + role + "\"}";
    }

    private static RequestPostProcessor senior() {
        return jwt().jwt(j -> j.claim("preferred_username", "boss")).authorities(
                new SimpleGrantedAuthority("ROLE_OPERATOR"), new SimpleGrantedAuthority("ROLE_SENIOR_OPERATOR"));
    }

    private static RequestPostProcessor as(String user, String role) {
        return jwt().jwt(j -> j.claim("preferred_username", user)).authorities(new SimpleGrantedAuthority(role));
    }
}
