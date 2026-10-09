package ru.subsidy.package_.integrations;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ru.subsidy.package_.config.AbstractIntegrationTest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void ping_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void ping_withSpecialistToken_returnsRoles() throws Exception {
        mockMvc.perform(get("/api/v1/ping").with(jwt()
                        .jwt(token -> token.subject("user-1").claim("roles", List.of("SPECIALIST")))
                        .authorities(new SimpleGrantedAuthority("ROLE_SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("package"));
    }
}
