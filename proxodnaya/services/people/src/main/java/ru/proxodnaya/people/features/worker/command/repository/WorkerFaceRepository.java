package ru.proxodnaya.people.features.worker.command.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.people.features.worker.model.FaceEnrollment;
import ru.proxodnaya.people.jooq.Tables;
import ru.proxodnaya.people.jooq.tables.WorkerFaces;

/**
 * Факт наличия эталона лица у рабочего (сам эталон — в face).
 */
@Repository
public class WorkerFaceRepository {

    private static final WorkerFaces FACES = Tables.WORKER_FACES;

    private final DSLContext dsl;

    public WorkerFaceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Запоминает заведённый эталон.
     *
     * @param workerId рабочий
     * @param result   итог face
     * @param enrolledAt когда
     * @param actor    кто завёл
     */
    public void save(UUID workerId, FaceEnrollment result, Instant enrolledAt, String actor) {
        dsl.insertInto(FACES)
                .set(FACES.WORKER_ID, workerId)
                .set(FACES.TEMPLATES, result.templates())
                .set(FACES.QUALITY, (float) result.quality())
                .set(FACES.ENROLLED_AT, enrolledAt.atOffset(ZoneOffset.UTC))
                .set(FACES.ENROLLED_BY, actor)
                .onConflict(FACES.WORKER_ID).doUpdate()
                .set(FACES.TEMPLATES, result.templates())
                .set(FACES.QUALITY, (float) result.quality())
                .set(FACES.ENROLLED_AT, enrolledAt.atOffset(ZoneOffset.UTC))
                .set(FACES.ENROLLED_BY, actor)
                .execute();
    }

    /**
     * Забывает эталон.
     *
     * @param workerId рабочий
     */
    public void delete(UUID workerId) {
        dsl.deleteFrom(FACES).where(FACES.WORKER_ID.eq(workerId)).execute();
    }
}
