package ru.documents.review;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.documents.review.features.staff.gateway.KeycloakAdminGateway;
import ru.documents.review.features.staff.gateway.StaffMailer;

/** APP_MODE=dev: быстрый вход тестовыми сотрудниками и код сразу в ответе; код всё равно проверяет Keycloak. */
@SpringBootTest(properties = {"app.staff.mode=dev", "app.staff.demo-emails=senior@documents.local"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffTestModeIntegrationTest extends AbstractIntegrationTest {

    private static final String MAIL = "senior@documents.local";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @MockitoBean
    private KeycloakAdminGateway keycloak;
    @MockitoBean
    private StaffMailer mailer;

    @Test
    void devModeListsTestUsersAndGivesCode() throws Exception {
        var user = json.valueToTree(Map.of("id", "k1", "username", "senior", "email", MAIL, "enabled", true,
                "firstName", "Сергей", "lastName", "Старший"));
        when(keycloak.users()).thenReturn(List.of((com.fasterxml.jackson.databind.JsonNode) user));
        when(keycloak.findByEmail(MAIL)).thenReturn(Optional.of(user));
        when(keycloak.realmRoles("k1")).thenReturn(List.of("OPERATOR", "SENIOR_OPERATOR"));
        mvc.perform(get("/api/v1/auth/mode")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("dev"))
                .andExpect(jsonPath("$.testUsers[0].email").value(MAIL))
                .andExpect(jsonPath("$.testUsers[0].role").value("SENIOR_OPERATOR"));
        mvc.perform(post("/api/v1/auth/test-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + MAIL + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("\\d{6}")));
        verify(keycloak).issueCode(eq("k1"), anyString(), eq(600L));
        mvc.perform(post("/api/v1/auth/test-code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@x.ru\"}")).andExpect(status().isNotFound());
    }

    @Test
    void invitedStaffCannotUseTestCode() throws Exception {
        String real = "ivanov@company.ru";
        var user = json.valueToTree(Map.of("id", "k2", "username", "ivanov", "email", real, "enabled", true,
                "firstName", "Иван", "lastName", "Иванов"));
        when(keycloak.users()).thenReturn(List.of((com.fasterxml.jackson.databind.JsonNode) user));
        when(keycloak.findByEmail(real)).thenReturn(Optional.of(user));
        when(keycloak.realmRoles("k2")).thenReturn(List.of("SENIOR_OPERATOR"));
        mvc.perform(get("/api/v1/auth/mode")).andExpect(status().isOk())
                .andExpect(jsonPath("$.testUsers").isEmpty());
        mvc.perform(post("/api/v1/auth/test-code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + real + "\"}")).andExpect(status().isNotFound());
        verify(keycloak, org.mockito.Mockito.never()).issueCode(eq("k2"), anyString(), anyLong());
    }
}
