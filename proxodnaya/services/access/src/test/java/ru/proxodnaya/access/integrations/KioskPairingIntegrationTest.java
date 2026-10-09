package ru.proxodnaya.access.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;

import ru.proxodnaya.access.config.AbstractIntegrationTest;
import ru.proxodnaya.access.features.kiosk.gateway.CheckpointGateway;
import ru.proxodnaya.access.features.kiosk.model.CheckpointView;
import ru.proxodnaya.access.features.pass.gateway.FaceGateway;
import ru.proxodnaya.access.features.pass.gateway.PeopleGateway;
import ru.proxodnaya.access.features.pass.gateway.ShiftGateway;
import ru.proxodnaya.access.features.pass.model.FaceResult;
import ru.proxodnaya.access.features.pass.model.WorkerProfile;
import ru.proxodnaya.access.features.qr.api.DeviceKeyProjection;
import ru.proxodnaya.access.support.TestKeys;

/**
 * Терминал (ACC-12): регистрация ключа → код → сопряжение в кабинете → подписанные запросы с проходной терминала.
 */
class KioskPairingIntegrationTest extends AbstractIntegrationTest {

    private static final String PREFIX = "/access";
    private static final String ME = "/api/v1/kiosk/me";
    private static final String SCAN = "/api/v1/kiosk/attempts";
    private static final String CHECKPOINT = "cp-kiosk";
    private static final String PAIR = "/api/v1/kiosks/pair";

    @MockitoBean
    private CheckpointGateway checkpoints;

    @MockitoBean
    private PeopleGateway people;

    @MockitoBean
    private FaceGateway face;

    @MockitoBean
    private ShiftGateway shift;

    @Autowired
    private DeviceKeyProjection devices;

    @Autowired
    private JdbcTemplate jdbc;

    private final TestKeys kioskKey = TestKeys.generate();

    @BeforeEach
    void setUp() {
        when(checkpoints.find(CHECKPOINT))
                .thenReturn(Optional.of(new CheckpointView(CHECKPOINT, "zone-k", "site-k", "Главный вход")));
        when(checkpoints.find("missing")).thenReturn(Optional.empty());
    }

    @Test
    void register_pair_me_scanUsesKioskCheckpoint() throws Exception {
        // given
        JsonNode reg = register();
        String kioskId = reg.get("kioskId").asText();
        String code = reg.get("pairCode").asText();
        assertThat(code).hasSize(6);
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kiosk.pairCode").value(code))
                .andExpect(jsonPath("$.checkpoint").doesNotExist())
                .andExpect(jsonPath("$.settings.terminalMode").exists());

        // when
        String pair = "{\"pairCode\":\"" + code.toLowerCase(Locale.ROOT) + "\",\"name\":\"Вход 1\","
                + "\"checkpointId\":\"" + CHECKPOINT + "\"}";
        mockMvc.perform(admin(post(PAIR)).content(pair))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkpointId").value(CHECKPOINT))
                .andExpect(jsonPath("$.pairCode").doesNotExist());

        // then
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kiosk.pairedAt").isNumber())
                .andExpect(jsonPath("$.checkpoint.name").value("Главный вход"));
        String workerId = "w" + UUID.randomUUID().toString().substring(0, 8);
        String deviceId = "d" + UUID.randomUUID().toString().substring(0, 8);
        TestKeys phone = TestKeys.generate();
        devices.bind(deviceId, workerId, phone.rawB64u(), Instant.now());
        when(people.accessProfile(workerId)).thenReturn(Optional.of(new WorkerProfile(workerId, "ACTIVE", "Пётр Иванов",
                "Монтажник", "СтройМонтаж", Set.of(deviceId), LocalDate.now().plusYears(1), Set.of("zone-k"))));
        when(people.checkpointZone(any())).thenReturn(Optional.of("zone-k"));
        when(face.verify(eq(workerId), anyList(), any())).thenReturn(new FaceResult(true, 0.93, true, 0.9, null));
        String body = "{\"qr\":\"" + phone.qr(workerId, deviceId, TestKeys.window(Instant.now())) + "\",\"checkpointId\":\"forged\"}";
        mockMvc.perform(signed(HttpMethod.POST, SCAN, kioskId, body, nonce()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("challenge"));
        assertThat(jdbc.queryForObject("select checkpoint_id from attempt_challenges where worker_id = ?", String.class, workerId))
                .isEqualTo(CHECKPOINT);
        mockMvc.perform(admin(get("/api/v1/kiosks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + kioskId + "')].name").value("Вход 1"));
    }

    @Test
    void scan_unpairedKiosk_kioskNotPaired() throws Exception {
        // given
        String kioskId = register().get("kioskId").asText();

        // when / then
        String body = "{\"qr\":\"v1.a.b.1.c\",\"checkpointId\":\"" + CHECKPOINT + "\"}";
        mockMvc.perform(signed(HttpMethod.POST, SCAN, kioskId, body, nonce()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KIOSK_NOT_PAIRED"));
    }

    @Test
    void signed_replayedNonce_rejected() throws Exception {
        // given
        String kioskId = register().get("kioskId").asText();
        String nonce = nonce();
        String sentAt = String.valueOf(System.currentTimeMillis());
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce, sentAt)).andExpect(status().isOk());

        // when / then
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce, sentAt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KIOSK_SIGNATURE_INVALID"));
    }

    @Test
    void signed_wrongKeyOrStaleClock_rejected() throws Exception {
        // given
        String kioskId = register().get("kioskId").asText();
        String sentAt = String.valueOf(System.currentTimeMillis());
        String message = "kiosk|" + kioskId + "|GET|" + PREFIX + ME + "|" + sentAt + "|n1|" + sha256("");

        // when / then: чужой ключ
        mockMvc.perform(get(ME).header("X-Kiosk-Id", kioskId).header("X-Ts", sentAt).header("X-Nonce", "n1")
                        .header("X-Signature", b64u(TestKeys.generate().signP1363(message))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KIOSK_SIGNATURE_INVALID"));
        // часы терминала на 10 минут позади
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce(), String.valueOf(System.currentTimeMillis() - 600_000)))
                .andExpect(status().isForbidden());
        // незнакомый терминал — регистрироваться заново
        mockMvc.perform(signed(HttpMethod.GET, ME, UUID.randomUUID().toString(), "", nonce()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("KIOSK_UNKNOWN"));
        // без подписи и без токена
        mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
    }

    @Test
    void pair_wrongCodeOrCheckpoint_andRoles() throws Exception {
        // given
        String code = register().get("pairCode").asText();

        // when / then
        mockMvc.perform(admin(post(PAIR)).content(pairBody("ZZZZZZ", CHECKPOINT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAIRING_CODE_INVALID"));
        mockMvc.perform(admin(post(PAIR)).content(pairBody(code, "missing")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PAIR).contentType(MediaType.APPLICATION_JSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUARD")))
                        .content(pairBody(code, CHECKPOINT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unpair_newCodeShown() throws Exception {
        // given
        JsonNode reg = register();
        String kioskId = reg.get("kioskId").asText();
        mockMvc.perform(admin(post(PAIR)).content(pairBody(reg.get("pairCode").asText(), CHECKPOINT)))
                .andExpect(status().isOk());

        // when
        mockMvc.perform(admin(post("/api/v1/kiosks/" + kioskId + "/unpair"))).andExpect(status().isNoContent());

        // then
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kiosk.pairedAt").doesNotExist())
                .andExpect(jsonPath("$.kiosk.pairCode").isString());
    }

    @AfterEach
    void clearTerminalCodes() {
        jdbc.update("delete from terminal_codes");
    }

    @Test
    void hashes_onlyPairedKiosk_cannotUsePinAsGuardAuthentication() throws Exception {
        // given
        mockMvc.perform(admin(put("/api/v1/terminal-codes/service")).content("{\"code\":\"482651\"}"))
                .andExpect(status().isOk());
        JsonNode reg = register();
        String kioskId = reg.get("kioskId").asText();
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codes").doesNotExist());

        // when
        mockMvc.perform(admin(post(PAIR)).content(pairBody(reg.get("pairCode").asText(), CHECKPOINT)))
                .andExpect(status().isOk());

        // then
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codes.service.salt").isString())
                .andExpect(jsonPath("$.codes.service.hash").isString())
                .andExpect(jsonPath("$.codes.service.iter").value(120_000))
                .andExpect(jsonPath("$.codes.service.digits").value(6))
                .andExpect(jsonPath("$.codes.service.code").doesNotExist());
        String manual = "{\"workerId\":\"worker\",\"checkpointId\":\"cp\",\"note\":\"Проверка\",\"guardCode\":\"482651\"}";
        mockMvc.perform(signed(HttpMethod.POST, "/api/v1/kiosk/manual", kioskId, manual, nonce()))
                .andExpect(status().isForbidden());
        mockMvc.perform(admin(post("/api/v1/kiosks/" + kioskId + "/unpair"))).andExpect(status().isNoContent());
        mockMvc.perform(signed(HttpMethod.GET, ME, kioskId, "", nonce()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codes").doesNotExist());
    }

    private JsonNode register() throws Exception {
        String response = mockMvc.perform(post("/api/v1/kiosk/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicKeyJwk\":" + kioskKey.jwk() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private MockHttpServletRequestBuilder signed(HttpMethod method, String path, String kioskId, String body, String nonce)
            throws Exception {
        return signed(method, path, kioskId, body, nonce, String.valueOf(System.currentTimeMillis()));
    }

    private MockHttpServletRequestBuilder signed(HttpMethod method, String path, String kioskId, String body, String nonce, String sentAt)
            throws Exception {
        String message = "kiosk|" + kioskId + "|" + method.name() + "|" + PREFIX + path + "|" + sentAt + "|" + nonce + "|" + sha256(body);
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, path)
                .header("X-Kiosk-Id", kioskId).header("X-Ts", sentAt).header("X-Nonce", nonce)
                .header("X-Signature", b64u(kioskKey.signP1363(message)));
        return body.isEmpty() ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.claim("name", "Админ")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static String pairBody(String code, String checkpointId) {
        return "{\"pairCode\":\"" + code + "\",\"name\":\"T\",\"checkpointId\":\"" + checkpointId + "\"}";
    }

    private static String nonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String sha256(String body) throws Exception {
        return b64u(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String b64u(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
