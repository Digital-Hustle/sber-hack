package ru.proxodnaya.assistant.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ru.proxodnaya.assistant.config.AbstractIntegrationTest;
import ru.proxodnaya.assistant.gateway.ShiftClient;
import ru.proxodnaya.assistant.llm.ChatRs;
import ru.proxodnaya.assistant.llm.LlmGateway;
import ru.proxodnaya.assistant.llm.LlmUnavailableException;
import ru.proxodnaya.assistant.llm.ToolCall;

/**
 * Цикл tool calling с подменённой моделью и shift: честный ответ, перегенерация, отказ с сырыми данными, недоступная модель.
 */
class AssistantIntegrationTest extends AbstractIntegrationTest {

    private static final String ASK = "/api/v1/assistant/ask";
    private static final String QUESTION = "{\"question\":\"Сколько часов отработал w42 сегодня?\"}";
    private static final String HOURS = "/analytics/hours";
    private static final String VALIDATION = "$.validation";

    @MockitoBean
    private LlmGateway llm;

    @MockitoBean
    private ShiftClient shift;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void shiftData() throws Exception {
        when(llm.provider()).thenReturn("ollama");
        when(llm.model()).thenReturn("qwen3:4b");
        when(shift.get(eq(HOURS), anyMap())).thenReturn(objectMapper.readTree("{\"items\":[{\"workerId\":\"w42\","
                + "\"workedMinutes\":750,\"intervals\":2,\"incomplete\":false}],\"method\":\"intervals\"}"));
    }

    @Test
    void numbersFromTool_ok_sourcesAndJournal() throws Exception {
        // given
        when(llm.chat(any())).thenReturn(toolCall(), text("w42 сегодня отработал 12,5 ч (2 интервала)."));

        // when / then
        mockMvc.perform(post(ASK).with(manager()).contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath(VALIDATION).value("OK"))
                .andExpect(jsonPath("$.sources[0].tool").value("worked_hours"))
                .andExpect(jsonPath("$.sources[0].ok").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from llm_requests where validation = 'OK'", Long.class)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from llm_requests where length(question_hash) <> 64", Long.class)).isZero();
    }

    @Test
    void inventedNumber_regeneratedOnce_thenRefusedWithRawData() throws Exception {
        // given: сначала 15 ч (нет в данных), после замечания — верно
        when(llm.chat(any())).thenReturn(toolCall(), text("w42 отработал 15 ч."), text("w42 отработал 12.5 ч."));

        // when / then
        mockMvc.perform(post(ASK).with(manager()).contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(jsonPath(VALIDATION).value("REGENERATED"))
                .andExpect(jsonPath("$.text").value("w42 отработал 12.5 ч."));

        // given: оба раза придумала
        when(llm.chat(any())).thenReturn(toolCall(), text("w42 отработал 15 ч."), text("Примерно 14 часов."));

        // when / then
        mockMvc.perform(post(ASK).with(manager()).contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(jsonPath(VALIDATION).value("REFUSED"))
                .andExpect(jsonPath("$.data[0].tool").value("worked_hours"))
                .andExpect(jsonPath("$.data[0].result.items[0].workedHours").value(12.5));
    }

    @Test
    void llmDown_503_status_rbac_validation() throws Exception {
        // given
        when(llm.chat(any())).thenThrow(new LlmUnavailableException("timeout", null));

        // when / then
        mockMvc.perform(post(ASK).with(manager()).contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        mockMvc.perform(get("/api/v1/assistant/status").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.lastOk").value(false));
        mockMvc.perform(post(ASK).with(role("WORKER")).contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ASK).with(manager()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    private static ChatRs toolCall() {
        return new ChatRs("", List.of(new ToolCall("c1", "worked_hours", "{\"workerId\":\"w42\"}")), null, null);
    }

    private static ChatRs text(String content) {
        return new ChatRs(content, List.of(), null, null);
    }

    private static RequestPostProcessor manager() {
        return role("MANAGER");
    }

    private static RequestPostProcessor role(String role) {
        return jwt().jwt(token -> token.subject("boss")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
