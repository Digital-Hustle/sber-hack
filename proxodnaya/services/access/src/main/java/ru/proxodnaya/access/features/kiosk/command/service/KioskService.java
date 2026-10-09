package ru.proxodnaya.access.features.kiosk.command.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import ru.proxodnaya.access.config.properties.KioskProperties;
import ru.proxodnaya.access.features.kiosk.command.repository.KioskRepository;
import ru.proxodnaya.access.features.kiosk.gateway.CheckpointGateway;
import ru.proxodnaya.access.features.kiosk.model.Kiosk;
import ru.proxodnaya.access.features.kiosk.model.KioskPatch;
import ru.proxodnaya.access.features.qr.api.SignatureCheck;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.common.exception.NotFoundException;

/**
 * Терминалы (ACC-12, ADR-038): регистрация ключа → код сопряжения на экране → привязка к проходной в кабинете.
 * Несопряжённый терминал никого не пропускает.
 */
@Slf4j
@Service
public class KioskService {

    /** Без похожих символов (0/O, 1/I/L): код вводят руками с экрана. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_ATTEMPTS = 20;
    private static final String NOT_FOUND = "Терминал не найден";

    private final KioskRepository repository;
    private final SignatureCheck signatures;
    private final CheckpointGateway checkpoints;
    private final KioskProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public KioskService(KioskRepository repository, SignatureCheck signatures, CheckpointGateway checkpoints,
                        KioskProperties props, Clock clock) {
        this.repository = repository;
        this.signatures = signatures;
        this.checkpoints = checkpoints;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Регистрирует ключ нового терминала.
     *
     * @param publicKeyJwk публичный ключ терминала (JWK EC P-256)
     * @return терминал с кодом сопряжения
     * @throws DomainException {@code VALIDATION_ERROR}, если ключ не P-256
     */
    public Kiosk register(String publicKeyJwk) {
        String key;
        try {
            key = signatures.normalize(publicKeyJwk);
        } catch (IllegalArgumentException error) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Ключ терминала должен быть EC P-256");
        }
        UUID id = UUID.randomUUID();
        repository.insert(id, key, freeCode(), clock.instant());
        log.info("kiosk registered kioskId={}", id);
        return require(id);
    }

    /**
     * Терминал.
     *
     * @param id id
     * @return терминал
     * @throws NotFoundException если нет
     */
    public Kiosk require(UUID id) {
        return repository.find(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /**
     * Проходная сопряжённого терминала — для решения о проходе.
     *
     * @param id терминал
     * @return проходная
     * @throws DomainException {@code KIOSK_NOT_PAIRED}, если терминал не привязан
     */
    public String checkpointOf(UUID id) {
        Kiosk kiosk = require(id);
        if (!kiosk.paired()) {
            throw new DomainException(ErrorCode.KIOSK_NOT_PAIRED, "Терминал не привязан к проходной");
        }
        return kiosk.checkpointId();
    }

    /**
     * Терминалы для кабинета: сопряжённые и ждущие сопряжения.
     *
     * @return терминалы
     */
    public List<Kiosk> list() {
        return repository.list(clock.instant().minus(props.waitingShown()));
    }

    /**
     * Привязывает терминал по коду с его экрана.
     *
     * @param pairCode код
     * @param patch    название, проходная, логика
     * @param pairedBy кто привязал
     * @return терминал
     * @throws DomainException {@code PAIRING_CODE_INVALID} или {@code NOT_FOUND} (проходной нет)
     */
    public Kiosk pair(String pairCode, KioskPatch patch, String pairedBy) {
        String code = pairCode == null ? "" : pairCode.trim().toUpperCase(Locale.ROOT);
        Kiosk kiosk = repository.findByCode(code)
                .orElseThrow(() -> new DomainException(ErrorCode.PAIRING_CODE_INVALID, "Код не найден: проверьте код на экране терминала"));
        requireCheckpoint(patch.checkpointId());
        if (!repository.pair(kiosk.id(), patch, pairedBy, clock.instant())) {
            throw new DomainException(ErrorCode.PAIRING_CODE_INVALID, "Терминал уже привязан");
        }
        log.info("kiosk paired kioskId={} checkpointId={}", kiosk.id(), patch.checkpointId());
        return require(kiosk.id());
    }

    /**
     * Меняет терминал.
     *
     * @param id    терминал
     * @param patch изменения
     * @return терминал
     * @throws NotFoundException если терминала или проходной нет
     */
    public Kiosk update(UUID id, KioskPatch patch) {
        if (patch.checkpointId() != null) {
            requireCheckpoint(patch.checkpointId());
        }
        if (!repository.update(id, patch)) {
            throw new NotFoundException(NOT_FOUND);
        }
        return require(id);
    }

    /**
     * Отвязывает терминал: на экране снова код сопряжения, ключ остаётся.
     *
     * @param id терминал
     * @throws NotFoundException если нет
     */
    public void unpair(UUID id) {
        if (!repository.unpair(id, freeCode())) {
            throw new NotFoundException(NOT_FOUND);
        }
        log.info("kiosk unpaired kioskId={}", id);
    }

    /**
     * Пульс терминала.
     *
     * @param id терминал
     */
    public void touch(UUID id) {
        repository.touch(id, clock.instant());
    }

    /**
     * Гасит nonce запроса терминала.
     *
     * @param id    терминал
     * @param nonce nonce
     * @return {@code true}, если nonce новый
     */
    public boolean useNonce(UUID id, String nonce) {
        Instant now = clock.instant();
        return repository.useNonce(id, nonce, now.plus(props.clockSkew()).plus(props.clockSkew()), now);
    }

    private void requireCheckpoint(String checkpointId) {
        if (checkpoints.find(checkpointId).isEmpty()) {
            throw new NotFoundException("Проходная не найдена");
        }
    }

    private String freeCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int pos = 0; pos < CODE_LENGTH; pos++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            if (!repository.codeTaken(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Не удалось подобрать свободный код сопряжения");
    }
}
