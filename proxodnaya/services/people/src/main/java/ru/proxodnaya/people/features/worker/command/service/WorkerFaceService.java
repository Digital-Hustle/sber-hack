package ru.proxodnaya.people.features.worker.command.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.proxodnaya.common.event.Topics;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.features.worker.command.repository.WorkerFaceRepository;
import ru.proxodnaya.people.features.worker.command.repository.WorkerRepository;
import ru.proxodnaya.people.features.worker.gateway.FaceGateway;
import ru.proxodnaya.people.features.worker.model.FaceCheck;
import ru.proxodnaya.people.features.worker.model.FaceEnrollment;
import ru.proxodnaya.people.messaging.outbox.Outbox;

/**
 * Эталон лица рабочего (PPL-12): снимки уходят в face, у себя храним только факт и качество.
 * Вызов face — вне транзакции: держать блокировку строки на время сети нельзя.
 */
@Service
public class WorkerFaceService {

    private final WorkerRepository workers;
    private final WorkerFaceRepository faces;
    private final FaceGateway face;
    private final Outbox outbox;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WorkerFaceService(WorkerRepository workers, WorkerFaceRepository faces, FaceGateway face, Outbox outbox,
                             Clock clock, PlatformTransactionManager transactions) {
        this.workers = workers;
        this.faces = faces;
        this.face = face;
        this.outbox = outbox;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /**
     * Заводит (переснимает) эталон.
     *
     * @param workerId рабочий
     * @param frames   1..3 снимка
     * @param actor    кто заводит
     * @return итог face
     */
    public FaceEnrollment enroll(UUID workerId, List<String> frames, String actor) {
        requireWorker(workerId);
        FaceEnrollment result = face.enroll(workerId, frames);
        transaction.executeWithoutResult(status -> {
            requireWorker(workerId);
            faces.save(workerId, result, clock.instant(), actor);
            outbox.add(Topics.PEOPLE_WORKER_V1, "WorkerFaceEnrolled", workerId.toString(),
                    Map.of("workerId", workerId.toString(), "templates", result.templates()));
        });
        return result;
    }

    /**
     * Проверяет снимок до сохранения карточки.
     *
     * @param photo снимок
     * @return вердикт face
     */
    public FaceCheck check(String photo) {
        return face.check(photo);
    }

    /**
     * Удаляет эталон: в face и у себя. Повтор безопасен.
     *
     * @param workerId рабочий
     */
    public void delete(UUID workerId) {
        requireWorker(workerId);
        forget(workerId);
    }

    /**
     * Удаляет эталон без проверки рабочего — для каскада при удалении карточки (PPL-10).
     *
     * @param workerId рабочий
     */
    public void forget(UUID workerId) {
        face.delete(workerId);
        transaction.executeWithoutResult(status -> faces.delete(workerId));
    }

    private void requireWorker(UUID workerId) {
        workers.lock(workerId).orElseThrow(() -> new DomainException(ErrorCode.WORKER_NOT_FOUND, "Рабочий не найден"));
    }
}
