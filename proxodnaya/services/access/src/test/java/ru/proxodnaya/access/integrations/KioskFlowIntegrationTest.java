package ru.proxodnaya.access.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.proxodnaya.access.config.AbstractIntegrationTest;
import ru.proxodnaya.access.features.pass.gateway.FaceGateway;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.gateway.ShiftGateway;
import ru.proxodnaya.access.features.pass.model.ActiveShift;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;
import ru.proxodnaya.access.features.qr.api.DeviceKeyProjection;
import ru.proxodnaya.access.support.TestKeys;

/**
 * Сквозной поток киоска через HTTP и PostgreSQL. face, people и shift подменены — это внешние сервисы.
 */
class KioskFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String SCAN = "/api/v1/kiosk/attempts";
    private static final String FRAMES = "/frames";
    private static final long NEXT_WINDOW_SECONDS = 30;

    @MockitoBean
    private FaceGateway face;

    @MockitoBean
    private PeopleGateway people;

    @MockitoBean
    private ShiftGateway shift;

    @Autowired
    private DeviceKeyProjection devices;

    @Autowired
    private JdbcTemplate jdbc;

    private final TestKeys keys = TestKeys.generate();
    private String workerId;
    private String deviceId;

    @BeforeEach
    void setUp() {
        workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
        deviceId = "d" + UUID.randomUUID().toString().substring(0, 8);
        devices.bind(deviceId, workerId, keys.rawB64u(), Instant.now());
        when(people.accessProfile(workerId)).thenReturn(Optional.of(new WorkerProfile(workerId, "ACTIVE", "Пётр Иванов",
                "Монтажник", "СтройМонтаж", Set.of(deviceId), LocalDate.now().plusYears(1), Set.of("zone-main"))));
        when(people.checkpointZone(any())).thenReturn(Optional.of("zone-main"));
        when(face.verify(eq(workerId), anyList(), any())).thenReturn(new FaceResult(true, 0.93, true, 0.9, null));
        when(shift.activeShift(eq(workerId), any()))
                .thenReturn(Optional.of(new ActiveShift("s1", Instant.now(), Instant.now(), true)));
    }

    @Test
    void pass_scanThenFrames_allowWithOutboxPresenceAndIntactJournal() throws Exception {
        // given
        String token = token(qrNow());

        // when / then
        mockMvc.perform(guard(post(SCAN + "/" + token + FRAMES)).content(framesBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.direction").value("IN"))
                .andExpect(jsonPath("$.worker.fullName").value("Пётр Иванов"))
                .andExpect(jsonPath("$.attemptId").value(token));
        assertThat(jdbc.queryForObject("select count(*) from outbox where message_key = ?", Integer.class, workerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select payload->'payload'->>'zoneId' from outbox where message_key = ?", String.class,
                workerId)).isEqualTo("zone-main");
        assertThat(jdbc.queryForObject("select inside from worker_state where worker_id = ?", Boolean.class, workerId)).isTrue();
        mockMvc.perform(post("/api/v1/security/journal/verify")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SECURITY_OFFICER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void frames_zoneWithoutPermit_noZonePermit() throws Exception {
        // given
        when(people.checkpointZone(any())).thenReturn(Optional.of("zone-closed"));
        String token = token(qrNow());

        // when / then
        mockMvc.perform(guard(post(SCAN + "/" + token + FRAMES)).content(framesBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.code").value("NO_ZONE_PERMIT"));
    }

    @Test
    void frames_zoneAtCapacity_zoneFull_thenFreeAfterExit() throws Exception {
        // given: в зоне на одно место уже стоит другой человек
        String zone = "zone-" + workerId;
        when(people.checkpointZone(any())).thenReturn(Optional.of(zone));
        when(people.zoneCapacity(any())).thenReturn(1);
        when(people.accessProfile(workerId)).thenReturn(Optional.of(new WorkerProfile(workerId, "ACTIVE", "Пётр Иванов",
                "Монтажник", "СтройМонтаж", Set.of(deviceId), LocalDate.now().plusYears(1), Set.of(zone))));
        jdbc.update("insert into worker_state (worker_id, inside, zone_id, last_in_at, updated_at) values (?, true, ?, now(), now())",
                "other-" + workerId, zone);

        // when / then
        mockMvc.perform(guard(post(SCAN + "/" + token(qrNow()) + FRAMES)).content(framesBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.code").value("ZONE_FULL"));
        assertThat(jdbc.queryForObject("select count(*) from worker_state where worker_id = ? and inside", Integer.class, workerId))
                .isZero();
        jdbc.update("update worker_state set inside = false where worker_id = ?", "other-" + workerId);
        mockMvc.perform(guard(post(SCAN + "/" + token(qrNext()) + FRAMES)).content(framesBody()))
                .andExpect(jsonPath("$.decision").value("ALLOW"));
        assertThat(jdbc.queryForObject("select zone_id from worker_state where worker_id = ?", String.class, workerId)).isEqualTo(zone);
    }

    @Test
    void scan_unknownCheckpoint_404() throws Exception {
        // given
        when(people.checkpointZone(any())).thenReturn(Optional.empty());

        // when / then
        mockMvc.perform(guard(post(SCAN)).content(scanBody(qrNow()))).andExpect(status().isNotFound());
    }

    @Test
    void pass_sameQrOnTwoKiosks_secondIsQrReused() throws Exception {
        // given
        String passQr = qrNow();
        String first = token(passQr);
        String second = token(passQr);

        // when
        mockMvc.perform(guard(post(SCAN + "/" + first + FRAMES)).content(framesBody()))
                .andExpect(jsonPath("$.decision").value("ALLOW"));

        // then
        mockMvc.perform(guard(post(SCAN + "/" + second + FRAMES)).content(framesBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.code").value("QR_REUSED"));
    }

    @Test
    void pass_twoChallengesOutside_secondIsAlreadyInside() throws Exception {
        // given: оба челленджа выданы, пока рабочий снаружи, — оба ждут IN
        long window = TestKeys.window(Instant.now());
        String first = token(keys.qr(workerId, deviceId, window));
        String second = token(keys.qr(workerId, deviceId, window + 1));
        mockMvc.perform(guard(post(SCAN + "/" + first + FRAMES)).content(framesBody()))
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.direction").value("IN"));

        // when / then
        mockMvc.perform(guard(post(SCAN + "/" + second + FRAMES)).content(framesBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.code").value("ALREADY_INSIDE"));
        assertThat(jdbc.queryForObject("select inside from worker_state where worker_id = ?", Boolean.class, workerId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from pass_attempts where claimed_worker_id = ? and decision = 'ALLOW'",
                Integer.class, workerId)).isEqualTo(1);
    }

    @Test
    void scan_rightAfterAllow_repeatScan() throws Exception {
        // given
        String token = token(qrNow());
        mockMvc.perform(guard(post(SCAN + "/" + token + FRAMES)).content(framesBody()));

        // when / then
        mockMvc.perform(guard(post(SCAN)).content(scanBody(keys.qr(workerId, deviceId, TestKeys.window(Instant.now()) + 1))))
                .andExpect(jsonPath("$.kind").value("decision"))
                .andExpect(jsonPath("$.result.code").value("REPEAT_SCAN"));
    }

    @Test
    void scan_signedByForeignKey_qrInvalid() throws Exception {
        // given
        String forged = TestKeys.generate().qr(workerId, deviceId, TestKeys.window(Instant.now()));

        // when / then
        mockMvc.perform(guard(post(SCAN)).content(scanBody(forged)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("decision"))
                .andExpect(jsonPath("$.result.decision").value("DENY"))
                .andExpect(jsonPath("$.result.code").value("QR_INVALID"))
                .andExpect(jsonPath("$.result.message").isNotEmpty());
    }

    @Test
    void frames_tokenUsedTwice_notFound() throws Exception {
        // given
        String token = token(qrNow());
        mockMvc.perform(guard(post(SCAN + "/" + token + FRAMES)).content(framesBody()));

        // when / then
        mockMvc.perform(guard(post(SCAN + "/" + token + FRAMES)).content(framesBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void scan_withoutTokenWrongRoleOrInvalidBody_401_403_400() throws Exception {
        // when / then
        mockMvc.perform(post(SCAN).contentType(MediaType.APPLICATION_JSON).content(scanBody("x")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(SCAN).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON).content(scanBody("x")))
                .andExpect(status().isForbidden());
        mockMvc.perform(guard(post(SCAN)).content("{\"qr\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void journal_updateOrDelete_rejectedByTrigger() {
        // when / then
        assertThatThrownBy(() -> jdbc.update("update pass_attempts set decision = 'ALLOW'")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from pass_attempts")).hasMessageContaining("append-only");
    }

    private String qrNow() {
        return keys.qr(workerId, deviceId, TestKeys.window(Instant.now()));
    }

    private String qrNext() {
        return keys.qr(workerId, deviceId, TestKeys.window(Instant.now().plusSeconds(NEXT_WINDOW_SECONDS)));
    }

    private String token(String passQr) throws Exception {
        String body = mockMvc.perform(guard(post(SCAN)).content(scanBody(passQr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("challenge"))
                .andExpect(jsonPath("$.challenge.kind").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("token").asText();
    }

    private static MockHttpServletRequestBuilder guard(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUARD"))).contentType(MediaType.APPLICATION_JSON);
    }

    private static String scanBody(String passQr) {
        return "{\"qr\":\"" + passQr + "\",\"checkpointId\":\"cp_main\"}";
    }

    private static String framesBody() {
        return "{\"frames\":[\"/9j/a\",\"/9j/b\",\"/9j/c\"]}";
    }
}
