package ru.documents.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import ru.documents.review.features.staff.service.Totp;

/** FE-UI-03: сотрудников заводит и выдаёт им коды только старший; секрет — только в ответе. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffIntegrationTest extends AbstractIntegrationTest {

    private static final String STAFF = "/api/v1/staff";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @MockitoBean
    private KeycloakAdminGateway keycloak;

    @Test
    void onlySeniorManagesStaff() throws Exception {
        mvc.perform(get(STAFF).with(as("op", "ROLE_OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get(STAFF).with(as("aud", "ROLE_AUDITOR"))).andExpect(status().isForbidden());
        mvc.perform(post(STAFF).with(as("op", "ROLE_OPERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content(body("ivanov", "OPERATOR"))).andExpect(status().isForbidden());
        verify(keycloak, never()).create(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void seniorCreatesOperatorWithCode() throws Exception {
        mvc.perform(post(STAFF).with(senior()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ivanov", "SENIOR_OPERATOR")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.login").value("ivanov"))
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andExpect(jsonPath("$.secret").isNotEmpty());
        verify(keycloak).create(eq("ivanov"), eq("Иван"), eq("Иванов"), anyString(),
                eq(List.of("OPERATOR", "SENIOR_OPERATOR")));
    }

    @Test
    void badLoginIsRejected() throws Exception {
        mvc.perform(post(STAFF).with(senior()).contentType(MediaType.APPLICATION_JSON).content(body("и в", "OPERATOR")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void seniorCannotDisableHimself() throws Exception {
        mvc.perform(put(STAFF + "/boss/enabled").with(senior()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_DISABLE"));
    }

    @Test
    void reissueReplacesCode() throws Exception {
        when(keycloak.find("ivanov")).thenReturn(Optional.of(json.valueToTree(Map.of("id", "k1", "username", "ivanov"))));
        when(keycloak.realmRoles("k1")).thenReturn(List.of("OPERATOR"));
        mvc.perform(post(STAFF + "/ivanov/code").with(senior())).andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty());
        verify(keycloak).replaceCode(eq("k1"), anyString());
    }

    @Test
    void base32MatchesRfc4648() {
        assertThat(Totp.base32("foobar")).isEqualTo("MZXW6YTBOI");
        assertThat(Totp.newSecret()).hasSize(32).matches("[A-Za-z0-9]+");
    }

    private static String body(String login, String role) {
        return "{\"login\":\"" + login + "\",\"firstName\":\"Иван\",\"lastName\":\"Иванов\",\"role\":\"" + role + "\"}";
    }

    private static RequestPostProcessor senior() {
        return jwt().jwt(j -> j.claim("preferred_username", "boss")).authorities(
                new SimpleGrantedAuthority("ROLE_OPERATOR"), new SimpleGrantedAuthority("ROLE_SENIOR_OPERATOR"));
    }

    private static RequestPostProcessor as(String user, String role) {
        return jwt().jwt(j -> j.claim("preferred_username", user)).authorities(new SimpleGrantedAuthority(role));
    }
}
