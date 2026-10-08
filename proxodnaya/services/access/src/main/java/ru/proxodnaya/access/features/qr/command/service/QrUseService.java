package ru.proxodnaya.access.features.qr.command.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.qr.api.QrUses;
import ru.proxodnaya.access.features.qr.command.repository.QrUseRepository;

/**
 * Гашение QR. Только в транзакции решения (Propagation.MANDATORY): гашение, журнал и outbox атомарны.
 */
@Service
public class QrUseService implements QrUses {

    private final QrUseRepository repository;

    public QrUseService(QrUseRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consume(String deviceId, long window, UUID attemptId, Instant moment) {
        return repository.insertIfAbsent(deviceId, window, attemptId, moment);
    }
}
