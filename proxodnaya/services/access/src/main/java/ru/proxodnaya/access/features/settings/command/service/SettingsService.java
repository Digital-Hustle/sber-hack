package ru.proxodnaya.access.features.settings.command.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.proxodnaya.access.config.properties.AccessProperties;
import ru.proxodnaya.access.features.settings.api.PassTuning;
import ru.proxodnaya.access.features.settings.command.repository.SettingsRepository;
import ru.proxodnaya.access.features.settings.model.PassSettings;

/**
 * Настройки прохода (ACC-18): хранятся в БД, по умолчанию — env. Путь прохода читает их через {@link PassTuning}
 * из кэша на {@value #CACHE_SECONDS} с — изменение доходит до всех экземпляров access за это время.
 */
@Service
public class SettingsService implements PassTuning {

    static final long CACHE_SECONDS = 5;
    private static final String GUARD = "GUARD";

    private final SettingsRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PassSettings defaults;
    private final AtomicReference<Cached> cached = new AtomicReference<>();

    public SettingsService(SettingsRepository repository, ObjectMapper objectMapper, Clock clock, AccessProperties props,
                           @Value("${access.face-threshold:0.363}") double faceThreshold) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.defaults = new PassSettings(faceThreshold, (int) props.qrTolerance().toSeconds(), Defaults.SHIFT_GRACE_MIN, true,
                (int) props.repeatScanCooldown().toSeconds(), (int) props.presenceTtl().toHours(), props.tempLockFailures(),
                (int) props.tempLockWindow().toSeconds(), new PassSettings.Terminal("QR_FACE", "GLOBAL"),
                new PassSettings.Offline(GUARD, Defaults.OFFLINE_HOURS, GUARD, true, "DENY"), null, null);
    }

    /**
     * Текущие настройки.
     *
     * @return настройки (из кэша)
     */
    public PassSettings current() {
        Cached snapshot = cached.get();
        Instant now = clock.instant();
        if (snapshot == null || now.isAfter(snapshot.loadedAt().plusSeconds(CACHE_SECONDS))) {
            snapshot = new Cached(load(), now);
            cached.set(snapshot);
        }
        return snapshot.settings();
    }

    /**
     * Сбрасывает кэш: следующее чтение пойдёт в БД.
     */
    public void evict() {
        cached.set(null);
    }

    /**
     * Сохраняет настройки целиком.
     *
     * @param settings  новые значения (проверены на уровне API)
     * @param updatedBy кто меняет
     * @return сохранённые
     * @throws IllegalStateException если настройки не сериализуются
     */
    @Transactional
    public PassSettings update(PassSettings settings, String updatedBy) {
        Instant now = clock.instant();
        PassSettings saved = new PassSettings(settings.faceThreshold(), settings.qrToleranceSec(), settings.shiftGraceMin(),
                settings.requireShift(), settings.repeatScanCooldownSec(), settings.presenceTtlHours(), settings.tempLockFailures(),
                settings.tempLockWindowSec(), settings.terminal(), settings.offline(), now, updatedBy);
        try {
            repository.save(objectMapper.writeValueAsString(saved), now, updatedBy);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
        cached.set(new Cached(saved, now));
        return saved;
    }

    @Override
    public Duration qrTolerance() {
        return Duration.ofSeconds(current().qrToleranceSec());
    }

    @Override
    public Duration repeatScanCooldown() {
        return Duration.ofSeconds(current().repeatScanCooldownSec());
    }

    @Override
    public Duration presenceTtl() {
        return Duration.ofHours(current().presenceTtlHours());
    }

    @Override
    public int tempLockFailures() {
        return current().tempLockFailures();
    }

    @Override
    public Duration tempLockWindow() {
        return Duration.ofSeconds(current().tempLockWindowSec());
    }

    @Override
    public double faceThreshold() {
        return current().faceThreshold();
    }

    @Override
    public boolean requireShift() {
        return current().requireShift();
    }

    private PassSettings load() {
        return repository.find().map(stored -> {
            try {
                return objectMapper.readValue(stored.json(), PassSettings.class);
            } catch (JsonProcessingException error) {
                return defaults;
            }
        }).orElse(defaults);
    }

    /** Значения по умолчанию, которых нет в env. */
    private static final class Defaults {
        static final int SHIFT_GRACE_MIN = 60;
        static final int OFFLINE_HOURS = 12;

        private Defaults() {
        }
    }

    private record Cached(PassSettings settings, Instant loadedAt) {
    }
}
