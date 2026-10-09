package ru.proxodnaya.people.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.proxodnaya.people.jooq.Tables.OUTBOX;
import static ru.proxodnaya.people.jooq.Tables.WORKER_FACES;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.config.AbstractIntegrationTest;
import ru.proxodnaya.people.features.worker.model.FaceCheck;
import ru.proxodnaya.people.features.worker.model.FaceEnrollment;
import ru.proxodnaya.people.features.worker.model.FaceRejectedException;

/**
 * Эталон лица (PPL-12) и каскад удаления в face (PPL-10). face подменён.
 */
class WorkerFaceIntegrationTest extends AbstractIntegrationTest {

    private static final String WORKERS = "/api/v1/workers";
    private static final String PHOTO = "/9j/4AAQSkZJRg==";
    private static final String ADMIN = "ADMIN";

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void resetFace() {
        reset(face);
    }

    @Test
    void enroll_storesFactShowsInCardAndWritesOutbox() throws Exception {
        // given
        UUID id = createWorker();
        when(face.enroll(eq(id), anyList())).thenReturn(new FaceEnrollment(2, 0.81, 2));

        // when
        mockMvc.perform(as(ADMIN, post(WORKERS + "/" + id + "/face")).content(frames(PHOTO, PHOTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.templates").value(2));

        // then
        mockMvc.perform(as(ADMIN, get(WORKERS + "/" + id)))
                .andExpect(jsonPath("$.faceEnrolledAt").isNotEmpty());
        assertThat(dsl.fetchValue(dsl.select(WORKER_FACES.ENROLLED_BY).from(WORKER_FACES).where(WORKER_FACES.WORKER_ID.eq(id))))
                .isEqualTo("admin-1");
        assertThat(dsl.fetchCount(OUTBOX, OUTBOX.MESSAGE_KEY.eq(id.toString())
                .and(OUTBOX.PAYLOAD.cast(String.class).contains("WorkerFaceEnrolled"))))
                .isEqualTo(1);
    }

    @Test
    void enroll_badPhoto_422WithReasonAndNothingStored() throws Exception {
        // given
        UUID id = createWorker();
        when(face.enroll(eq(id), anyList()))
                .thenThrow(new FaceRejectedException(ErrorCode.FACE_LOW_QUALITY, "Слишком темно — добавьте света", "TOO_DARK"));

        // when / then
        mockMvc.perform(as(ADMIN, post(WORKERS + "/" + id + "/face")).content(frames(PHOTO)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FACE_LOW_QUALITY"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_DARK"));
        assertThat(dsl.fetchCount(WORKER_FACES, WORKER_FACES.WORKER_ID.eq(id))).isZero();
        mockMvc.perform(as(ADMIN, get(WORKERS + "/" + id))).andExpect(jsonPath("$.faceEnrolledAt").isEmpty());
    }

    @Test
    void enroll_validationRolesAndUnknownWorker() throws Exception {
        // given
        UUID id = createWorker();

        // when / then
        mockMvc.perform(as(ADMIN, post(WORKERS + "/" + id + "/face")).content(frames()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(ADMIN, post(WORKERS + "/" + id + "/face")).content(frames(PHOTO, PHOTO, PHOTO, PHOTO)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as("MANAGER", post(WORKERS + "/" + id + "/face")).content(frames(PHOTO)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(ADMIN, post(WORKERS + "/" + UUID.randomUUID() + "/face")).content(frames(PHOTO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKER_NOT_FOUND"));
        verify(face, never()).enroll(any(), anyList());
    }

    @Test
    void enroll_faceDown_503() throws Exception {
        // given
        UUID id = createWorker();
        when(face.enroll(eq(id), anyList())).thenThrow(new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "недоступен"));

        // when / then
        mockMvc.perform(as(ADMIN, post(WORKERS + "/" + id + "/face")).content(frames(PHOTO)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void check_returnsVerdictForManager() throws Exception {
        // given
        when(face.check(PHOTO)).thenReturn(new FaceCheck(false, "BLURRY", 0.2, "Снимок смазан"));

        // when / then
        mockMvc.perform(as("MANAGER", post(WORKERS + "/face/check")).content(objectMapper.writeValueAsString(Map.of("photo", PHOTO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("BLURRY"))
                .andExpect(jsonPath("$.message").value("Снимок смазан"));
    }

    @Test
    void deleteWorker_removesTemplatesInFaceFirst() throws Exception {
        // given
        UUID id = createWorker();
        when(face.enroll(eq(id), anyList())).thenReturn(new FaceEnrollment(1, 0.9, 1));
        mockMvc.perform(as(ADMIN, post(WORKERS + "/" + id + "/face")).content(frames(PHOTO))).andExpect(status().isOk());

        // when
        mockMvc.perform(as(ADMIN, delete(WORKERS + "/" + id))).andExpect(status().isNoContent());

        // then
        verify(face).delete(id);
        assertThat(dsl.fetchCount(WORKER_FACES, WORKER_FACES.WORKER_ID.eq(id))).isZero();
        mockMvc.perform(as(ADMIN, get(WORKERS + "/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void deleteWorker_faceDown_503AndWorkerKept() throws Exception {
        // given
        UUID id = createWorker();
        doThrow(new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "недоступен")).when(face).delete(id);

        // when / then
        mockMvc.perform(as(ADMIN, delete(WORKERS + "/" + id))).andExpect(status().isServiceUnavailable());
        mockMvc.perform(as(ADMIN, get(WORKERS + "/" + id))).andExpect(status().isOk());
    }

    @Test
    void deleteFace_onlyTemplates() throws Exception {
        // given
        UUID id = createWorker();
        when(face.enroll(eq(id), anyList())).thenReturn(new FaceEnrollment(1, 0.9, 1));
        mockMvc.perform(as(ADMIN, post(WORKERS + "/" + id + "/face")).content(frames(PHOTO))).andExpect(status().isOk());

        // when
        mockMvc.perform(as(ADMIN, delete(WORKERS + "/" + id + "/face"))).andExpect(status().isNoContent());

        // then
        verify(face).delete(id);
        mockMvc.perform(as(ADMIN, get(WORKERS + "/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faceEnrolledAt").isEmpty());
    }

    private UUID createWorker() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("fullName", "Лицов Пётр", "position", "Монтажник", "contractor", "А"));
        String json = mockMvc.perform(as(ADMIN, post(WORKERS)).content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).path("id").asText());
    }

    private String frames(String... photos) throws Exception {
        return objectMapper.writeValueAsString(Map.of("frames", List.of(photos)));
    }

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject("admin-1")).authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON);
    }
}
