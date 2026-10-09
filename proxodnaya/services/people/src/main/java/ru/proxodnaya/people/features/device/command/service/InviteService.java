package ru.proxodnaya.people.features.device.command.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.people.config.properties.PeopleProperties;
import ru.proxodnaya.people.features.device.command.repository.InviteRepository;
import ru.proxodnaya.people.features.device.model.IssuedInvite;
import ru.proxodnaya.people.features.worker.api.WorkerDirectory;
import ru.proxodnaya.people.features.worker.api.WorkerCard;
import ru.proxodnaya.people.shared.WorkerStatus;

/**
 * Выдача инвайтов (PPL-03): одноразовый код со сроком, прежние неиспользованные отзываются.
 */
@Service
public class InviteService {

    private static final int ATTEMPTS = 5;

    private final InviteRepository invites;
    private final WorkerDirectory workers;
    private final InviteCodes codes;
    private final PeopleProperties properties;
    private final Clock clock;

    public InviteService(InviteRepository invites, WorkerDirectory workers, InviteCodes codes,
                         PeopleProperties properties, Clock clock) {
        this.invites = invites;
        this.workers = workers;
        this.codes = codes;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Выдаёт новый инвайт рабочему.
     *
     * @param workerId рабочий
     * @return код и ссылка (показываются один раз)
     * @throws DomainException WORKER_NOT_FOUND, WORKER_BLOCKED
     * @throws IllegalStateException если пять кодов подряд заняты (практически невозможно)
     */
    @Transactional
    public IssuedInvite issue(UUID workerId) {
        WorkerCard worker = workers.lock(workerId)
                .orElseThrow(() -> new DomainException(ErrorCode.WORKER_NOT_FOUND, "Рабочий не найден"));
        if (worker.status() != WorkerStatus.ACTIVE) {
            throw new DomainException(ErrorCode.WORKER_BLOCKED, "Рабочий заблокирован — сначала разблокируйте");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.invite().ttl());
        invites.revokeOpen(workerId, now);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String code = codes.generate();
            if (invites.insert(workerId, codes.hash(code), now, expiresAt)) {
                String link = properties.invite().linkBase().replaceAll("/+$", "") + "/worker/activate?c=" + code;
                return new IssuedInvite(code, link, expiresAt);
            }
        }
        throw new IllegalStateException("Не удалось подобрать свободный код инвайта");
    }
}
