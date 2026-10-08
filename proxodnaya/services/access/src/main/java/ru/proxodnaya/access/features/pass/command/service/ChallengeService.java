package ru.proxodnaya.access.features.pass.command.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.pass.command.repository.ChallengeRepository;
import ru.proxodnaya.access.features.pass.model.PendingAttempt;

/**
 * Хранит попытку между шагами. Токен одноразовый.
 */
@Service
public class ChallengeService {

    private static final Duration KEEP_EXPIRED = Duration.ofMinutes(1);
    private static final long CLEANUP_EVERY_MS = 60_000;

    private final ChallengeRepository repository;
    private final Clock clock;

    public ChallengeService(ChallengeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Забирает попытку по токену.
     *
     * @param token токен из ответа на скан
     * @return попытка или пусто (нет или уже забрана)
     */
    @Transactional
    public Optional<PendingAttempt> take(UUID token) {
        return repository.take(token);
    }

    /**
     * Сохраняет попытку.
     *
     * @param attempt попытка
     */
    @Transactional
    public void create(PendingAttempt attempt) {
        repository.insert(attempt);
    }

    /**
     * Чистит попытки, на которые киоск так и не прислал кадры.
     */
    @Scheduled(fixedDelay = CLEANUP_EVERY_MS)
    @Transactional
    public void deleteAbandoned() {
        repository.deleteExpired(clock.instant().minus(KEEP_EXPIRED));
    }
}
