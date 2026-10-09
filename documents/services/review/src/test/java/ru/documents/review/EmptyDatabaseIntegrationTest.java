package ru.documents.review;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** RV-13 / FR-56: пустая схема → честные нули и «нет данных», а не примеры. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmptyDatabaseIntegrationTest {

    private static final String SCHEMA = "e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void schema(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql",
                () -> "create schema if not exists " + SCHEMA + "; set search_path to " + SCHEMA);
        r.add("spring.liquibase.default-schema", () -> SCHEMA);
    }

    @Test
    void emptyDatabaseShowsZeros() throws Exception {
        var op = jwt().jwt(j -> j.claim("preferred_username", "op1"))
                .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        mvc.perform(get("/api/v1/applications").with(op)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.page.total").value(0));
        mvc.perform(get("/api/v1/metrics").with(op)).andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting.count").value(0)).andExpect(jsonPath("$.waiting.p50").doesNotExist())
                .andExpect(jsonPath("$.decision.count").value(0)).andExpect(jsonPath("$.fieldsTotal").value(0))
                .andExpect(jsonPath("$.formulas.waiting").exists());
        mvc.perform(get("/api/v1/reports/expiring").with(op)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(post("/api/v1/applications/next").with(op)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUEUE_EMPTY"));
    }
}
