package ru.proxodnaya.people.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.proxodnaya.people.jooq.Tables.OUTBOX;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import ru.proxodnaya.people.config.AbstractIntegrationTest;

/**
 * Рабочие через HTTP: роли, валидация, курсор, outbox.
 */
class WorkerApiIntegrationTest extends AbstractIntegrationTest {

    private static final String WORKERS = "/api/v1/workers";

    @Autowired
    private DSLContext dsl;

    @Test
    void create_asAdmin_returnsWorkerAndWritesOutbox() throws Exception {
        // given
        String body = json(Map.of("fullName", "  Иванов   Иван ", "position", "Монтажник", "contractor", "СтройМонтаж"));

        // when
        JsonNode created = read(mockMvc.perform(as("ADMIN", post(WORKERS).content(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Иванов Иван"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString());

        // then
        String id = created.path("id").asText();
        String payload = dsl.select(OUTBOX.PAYLOAD).from(OUTBOX).where(OUTBOX.MESSAGE_KEY.eq(id)).fetchOne(OUTBOX.PAYLOAD).data();
        JsonNode event = objectMapper.readTree(payload);
        assertThat(event.path("type").asText()).isEqualTo("WorkerCreated");
        assertThat(event.path("payload").path("workerId").asText()).isEqualTo(id);
        assertThat(event.path("payload").path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void create_invalidBody_400WithFieldErrors() throws Exception {
        // given
        String body = json(Map.of("fullName", " ", "position", "Монтажник"));

        // when / then
        mockMvc.perform(as("ADMIN", post(WORKERS).content(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("fullName")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("contractor")));
    }

    @Test
    void roles_withoutToken401_managerCannotWrite403_managerCanRead() throws Exception {
        // given
        String body = json(Map.of("fullName", "Петров", "position", "Сварщик", "contractor", "А"));

        // when / then
        mockMvc.perform(post(WORKERS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as("MANAGER", post(WORKERS).content(body))).andExpect(status().isForbidden());
        mockMvc.perform(as("GUARD", get(WORKERS))).andExpect(status().isForbidden());
        mockMvc.perform(as("MANAGER", get(WORKERS))).andExpect(status().isOk());
    }

    @Test
    void blockUnblockDelete_changeStatusAndHideDeleted() throws Exception {
        // given
        String id = createWorker("Сидоров Блок", "Бетонщик", "Альфа");

        // when / then
        mockMvc.perform(as("ADMIN", post(WORKERS + "/" + id + "/block")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        mockMvc.perform(as("ADMIN", post(WORKERS + "/" + id + "/unblock")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(as("ADMIN", delete(WORKERS + "/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(as("ADMIN", get(WORKERS + "/" + id)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORKER_NOT_FOUND"));
        List<String> types = dsl.select(OUTBOX.PAYLOAD).from(OUTBOX).where(OUTBOX.MESSAGE_KEY.eq(id))
                .orderBy(OUTBOX.CREATED_AT).fetch(row -> readUnchecked(row.value1().data()).path("type").asText());
        assertThat(types).containsExactly("WorkerCreated", "WorkerBlocked", "WorkerUpdated", "WorkerDeleted");
    }

    @Test
    void update_patchesOnlyGivenFields() throws Exception {
        // given
        String id = createWorker("Кузнецов", "Разнорабочий", "Бета");

        // when / then
        mockMvc.perform(as("ADMIN", patch(WORKERS + "/" + id).content(json(Map.of("position", "Прораб")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("Прораб"))
                .andExpect(jsonPath("$.fullName").value("Кузнецов"));
        mockMvc.perform(as("ADMIN", patch(WORKERS + "/" + id).content(json(Map.of("contractor", "  ")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", patch(WORKERS + "/" + UUID.randomUUID()).content(json(Map.of("position", "X")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_cursorWalksAllPagesWithoutDuplicates_andSearchEscapesWildcards() throws Exception {
        // given
        String tag = "Курсор" + UUID.randomUUID().toString().substring(0, 8);
        for (int idx = 0; idx < 5; idx++) {
            createWorker(tag + " " + idx, "Монтажник", "Гамма");
        }
        createWorker("Процент 100%", "Монтажник", "Гамма");

        // when
        Set<String> seen = new HashSet<>();
        List<String> order = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = get(WORKERS).param("q", tag).param("limit", "2");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = read(mockMvc.perform(as("ADMIN", request)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(page.path("page").path("total").asLong()).isEqualTo(5);
            page.path("items").forEach(item -> {
                order.add(item.path("fullName").asText());
                seen.add(item.path("id").asText());
            });
            cursor = page.path("page").path("next").isNull() ? null : page.path("page").path("next").asText();
        } while (cursor != null);

        // then
        assertThat(seen).hasSize(5);
        assertThat(order).containsExactly(tag + " 4", tag + " 3", tag + " 2", tag + " 1", tag + " 0");
        mockMvc.perform(as("ADMIN", get(WORKERS).param("q", "%")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.total").value(1));
        mockMvc.perform(as("ADMIN", get(WORKERS).param("limit", "500"))).andExpect(status().isBadRequest());
        mockMvc.perform(as("ADMIN", get(WORKERS).param("filter", "inside"))).andExpect(status().isBadRequest());
    }

    private String createWorker(String fullName, String position, String contractor) throws Exception {
        String body = json(Map.of("fullName", fullName, "position", position, "contractor", contractor));
        return read(mockMvc.perform(as("ADMIN", post(WORKERS).content(body))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private JsonNode readUnchecked(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }
}
