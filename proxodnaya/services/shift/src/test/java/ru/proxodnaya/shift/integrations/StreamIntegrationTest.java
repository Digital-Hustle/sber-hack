package ru.proxodnaya.shift.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import ru.proxodnaya.shift.config.AbstractIntegrationTest;
import ru.proxodnaya.shift.features.interval.command.service.IntervalService;
import ru.proxodnaya.shift.features.interval.model.PassEvent;

/**
 * SSE (SH-08): тикет, обстановка, поток рабочего, досылка по Last-Event-ID.
 */
class StreamIntegrationTest extends AbstractIntegrationTest {

    private static final String TICKET = "/api/v1/stream/ticket";
    private static final String SITUATION = "/api/v1/stream/situation";
    private static final String WORKER = "/api/v1/stream/worker";
    private static final String PARAM = "ticket";
    private static final String ZONE = "zone-main";
    private static final String ALLOW = "ALLOW";
    private static final String CODE = "$.code";
    private static final String FORBIDDEN = "FORBIDDEN";

    @Autowired
    private IntervalService intervals;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private Clock clock;

    private String workerId;
    private Instant now;

    @BeforeEach
    void setUp() {
        workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
        now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void ticket_requiresToken() throws Exception {
        mockMvc.perform(get(TICKET)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(SITUATION).param(PARAM, "nope")).andExpect(status().isForbidden())
                .andExpect(jsonPath(CODE).value(FORBIDDEN));
    }

    @Test
    void ticket_oneTimeAndBoundToStream() throws Exception {
        // given
        String staff = ticket("MANAGER", "manager-1");
        String worker = ticket("WORKER", workerId);

        // when / then
        mockMvc.perform(get(SITUATION).param(PARAM, staff)).andExpect(request().asyncStarted());
        mockMvc.perform(get(SITUATION).param(PARAM, staff)).andExpect(status().isForbidden());
        mockMvc.perform(get(SITUATION).param(PARAM, worker)).andExpect(status().isForbidden());
    }

    @Test
    void situation_getsAttemptsAndPresenceAfterCommit() throws Exception {
        // given
        MvcResult stream = open(SITUATION, ticket("SECURITY_OFFICER", "so-1"), null);

        // when
        pass(workerId, "IN", ALLOW);
        pass(null, "IN", "DENY");

        // then
        String body = stream.getResponse().getContentAsString();
        assertThat(body).contains("event:attempt.created").contains("event:presence.changed").contains(workerId)
                .contains("\"decision\":\"DENY\"");
    }

    @Test
    void worker_getsOnlyOwnResults() throws Exception {
        // given
        String other = "w" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult stream = open(WORKER, ticket("WORKER", workerId), null);

        // when
        pass(other, "IN", ALLOW);
        pass(workerId, "IN", ALLOW);

        // then
        String body = stream.getResponse().getContentAsString();
        assertThat(body).contains("event:attempt.result").doesNotContain("presence.changed").doesNotContain(other);
        assertThat(count(body, "event:attempt.result")).isEqualTo(1);
    }

    @Test
    void lastEventId_replaysMissed() throws Exception {
        // given
        MvcResult first = open(SITUATION, ticket("ADMIN", "admin-1"), null);
        pass(workerId, "IN", ALLOW);
        String lastId = firstId(first.getResponse().getContentAsString());
        pass(workerId, "OUT", ALLOW);

        // when
        MvcResult again = open(SITUATION, ticket("ADMIN", "admin-1"), lastId);

        // then
        String body = again.getResponse().getContentAsString();
        assertThat(body).doesNotContain("id:" + lastId + "\n").contains("\"direction\":\"OUT\"")
                .doesNotContain("\"direction\":\"IN\"");
    }

    private String ticket(String role, String subject) throws Exception {
        String json = mockMvc.perform(get(TICKET).with(jwt().jwt(token -> token.subject(subject))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.path("stream").asText()).isEqualTo("WORKER".equals(role) ? "worker" : "situation");
        return node.path(PARAM).asText();
    }

    private MvcResult open(String path, String ticket, String lastEventId) throws Exception {
        var builder = get(path).param(PARAM, ticket).accept(MediaType.TEXT_EVENT_STREAM);
        if (lastEventId != null) {
            builder.header("Last-Event-ID", lastEventId);
        }
        return mockMvc.perform(builder).andExpect(request().asyncStarted()).andReturn();
    }

    private void pass(String worker, String direction, String decision) {
        PassEvent event = new PassEvent(UUID.randomUUID(), worker, "cp_main", ZONE, direction, decision, "OK", "KIOSK", now);
        transactions.executeWithoutResult(status -> intervals.onPass(event));
    }

    private static String firstId(String body) {
        Matcher matcher = Pattern.compile("id:(\\S+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static int count(String body, String needle) {
        int found = 0;
        for (int index = body.indexOf(needle); index >= 0; index = body.indexOf(needle, index + 1)) {
            found++;
        }
        return found;
    }
}
