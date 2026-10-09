package ru.proxodnaya.people.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import ru.proxodnaya.people.config.AbstractIntegrationTest;
import ru.proxodnaya.people.jooq.Tables;

/**
 * Вход персонала по коду из письма и управление доступом (PPL-13, ADR-041).
 */
class StaffLoginIntegrationTest extends AbstractIntegrationTest {

    private static final String ROOT = "root@gate.test";
    private static final String CODE = "/api/v1/auth/staff/code";
    private static final String VERIFY = "/api/v1/auth/staff/code/verify";
    private static final String REFRESH = "/api/v1/auth/staff/refresh";
    private static final String STAFF = "/api/v1/staff";

    @Autowired
    private DSLContext dsl;

    @Test
    void bootstrapAdmin_logsInByCode_getsPeopleToken_andSession() throws Exception {
        // given
        JsonNode sent = requestCode(ROOT);
        String code = mailedCode(ROOT);

        // when
        JsonNode session = login(ROOT, code);

        // then
        assertThat(sent.path("sentTo").asText()).isEqualTo("roo•••••@gate.test");
        assertThat(sent.path("resendAt").asLong()).isLessThan(sent.path("expiresAt").asLong());
        assertThat(session.path("user").path("role").asText()).isEqualTo("ADMIN");
        assertThat(session.path("user").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(session.path("sessionExpiresAt").asLong()).isGreaterThan(session.path("expiresAt").asLong());
        JWTClaimsSet claims = SignedJWT.parse(session.path("accessToken").asText()).getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("https://gate.test/people");
        assertThat(claims.getStringClaim("email")).isEqualTo(ROOT);
        assertThat(claims.getJSONObjectClaim("realm_access").get("roles")).isEqualTo(List.of("ADMIN"));
        mockMvc.perform(withSession(get("/api/v1/auth/staff/me"), session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ROOT));
        mockMvc.perform(withSession(post("/api/v1/auth/staff/logout"), session)).andExpect(status().isNoContent());
        mockMvc.perform(withSession(get("/api/v1/auth/staff/me"), session))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mockMvc.perform(as("ADMIN", get(STAFF + "/access-log"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("LOGOUT"));
    }

    @Test
    void wrongCode_countsAttempts_thenLocks_resendTooSoon_andExpiry() throws Exception {
        // given
        requestCode(ROOT);
        mailedCode(ROOT);

        // when / then
        mockMvc.perform(json(post(CODE), Map.of("email", ROOT)))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_RESEND_TOO_SOON"));
        mockMvc.perform(json(post(VERIFY), Map.of("email", ROOT, "code", "000000")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOGIN_CODE_INVALID"))
                .andExpect(jsonPath("$.detail").value("Неверный код. Осталось попыток: 4"));
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(json(post(VERIFY), Map.of("email", ROOT, "code", "000000"))).andExpect(status().isBadRequest());
        }
        mockMvc.perform(json(post(VERIFY), Map.of("email", ROOT, "code", "000000")))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_CODE_LOCKED"));

        // истёк
        dsl.update(Tables.STAFF_LOGIN_CODES).set(Tables.STAFF_LOGIN_CODES.RESEND_AT, OffsetDateTime.now().minusMinutes(1))
                .where(Tables.STAFF_LOGIN_CODES.EMAIL.eq(ROOT)).execute();
        requestCode(ROOT);
        String code = mailedCode(ROOT);
        dsl.update(Tables.STAFF_LOGIN_CODES).set(Tables.STAFF_LOGIN_CODES.EXPIRES_AT, OffsetDateTime.now().minusSeconds(1))
                .where(Tables.STAFF_LOGIN_CODES.EMAIL.eq(ROOT)).execute();
        mockMvc.perform(json(post(VERIFY), Map.of("email", ROOT, "code", code)))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("LOGIN_CODE_EXPIRED"));
        dsl.deleteFrom(Tables.STAFF_LOGIN_CODES).where(Tables.STAFF_LOGIN_CODES.EMAIL.eq(ROOT)).execute();
    }

    @Test
    void unknownEmail_sameAnswer_noMail_andNoLogin() throws Exception {
        // given
        String ghost = "ghost-" + UUID.randomUUID().toString().substring(0, 6) + "@gate.test";

        // when
        JsonNode sent = requestCode(ghost);

        // then
        assertThat(sent.path("sentTo").asText()).endsWith("@gate.test");
        verify(mailer, never()).loginCode(eq(ghost), anyString(), anyLong());
        mockMvc.perform(json(post(VERIFY), Map.of("email", ghost, "code", "123456")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOGIN_CODE_INVALID"));
    }

    @Test
    void admin_invitesGuard_changesRole_disables_sessionEnds_lastAdminProtected() throws Exception {
        // given
        String email = "guard-" + UUID.randomUUID().toString().substring(0, 6) + "@gate.test";
        final String guardId = body(mockMvc.perform(as("ADMIN", post(STAFF))
                        .content(objectMapper.writeValueAsString(Map.of("name", "Охранник Тест", "email", email, "role", "GUARD"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVITED")).andReturn().getResponse()
                .getContentAsString()).path("id").asText();
        verify(mailer).invitation(eq(email), eq("Охранник Тест"), eq("Охранник"), anyString());
        mockMvc.perform(as("ADMIN", post(STAFF)).content(objectMapper.writeValueAsString(Map.of("name", "Дубль", "email",
                email.toUpperCase(), "role", "GUARD")))).andExpect(status().isConflict());
        requestCode(email);
        JsonNode session = login(email, mailedCode(email));

        // when / then
        assertThat(session.path("user").path("status").asText()).isEqualTo("ACTIVE");
        mockMvc.perform(as("ADMIN", patch(STAFF + "/" + guardId)).content("{\"role\":\"SECURITY_OFFICER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SECURITY_OFFICER"));
        JsonNode refreshed = body(mockMvc.perform(json(post(REFRESH), Map.of("refreshToken", session.path("refreshToken").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(SignedJWT.parse(refreshed.path("accessToken").asText()).getJWTClaimsSet().getJSONObjectClaim("realm_access")
                .get("roles")).isEqualTo(List.of("SECURITY_OFFICER"));
        mockMvc.perform(json(post(REFRESH), Map.of("refreshToken", session.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(as("ADMIN", post(STAFF + "/" + guardId + "/disable"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(withSession(get("/api/v1/auth/staff/me"), refreshed)).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post(REFRESH), Map.of("refreshToken", refreshed.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as("ADMIN", post(STAFF + "/" + guardId + "/enable"))).andExpect(jsonPath("$.status").value("ACTIVE"));

        String rootId = dsl.select(Tables.STAFF_USERS.ID).from(Tables.STAFF_USERS).where(Tables.STAFF_USERS.EMAIL.eq(ROOT))
                .fetchOne(Tables.STAFF_USERS.ID).toString();
        requestCode(ROOT);
        login(ROOT, mailedCode(ROOT));
        mockMvc.perform(as("ADMIN", post(STAFF + "/" + rootId + "/disable")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        mockMvc.perform(as("ADMIN", patch(STAFF + "/" + rootId)).content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        mockMvc.perform(as("MANAGER", get(STAFF))).andExpect(status().isForbidden());
        mockMvc.perform(as("ADMIN", get(STAFF).param("role", "SECURITY_OFFICER").param("q", email.substring(0, 10))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(guardId))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    private JsonNode requestCode(String email) throws Exception {
        return body(mockMvc.perform(json(post(CODE), Map.of("email", email))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String mailedCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).loginCode(eq(email), code.capture(), eq(10L));
        return code.getValue();
    }

    private JsonNode login(String email, String code) throws Exception {
        return body(mockMvc.perform(json(post(VERIFY), Map.of("email", email.toUpperCase(), "code", code.substring(0, 3) + " "
                        + code.substring(3))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request, JsonNode session) throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(session.path("accessToken").asText()).getJWTClaimsSet();
        return request.with(jwt().jwt(token -> token.subject(claims.getSubject()).claim("sid", claims.getClaim("sid"))));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, String> payload) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload));
    }

    private JsonNode body(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject("kc-" + role).claim("name", "Тест " + role))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
