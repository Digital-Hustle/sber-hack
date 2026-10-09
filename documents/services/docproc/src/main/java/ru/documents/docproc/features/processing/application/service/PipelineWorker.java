package ru.documents.docproc.features.processing.application.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.documents.docproc.config.DocprocProperties;
import ru.documents.docproc.features.processing.command.service.JobCommandService;
import ru.documents.docproc.features.processing.model.Job;

/**
 * Пул воркеров (DOCPROC_WORKERS): захват заданий из БД и продление аренды, пока шаг выполняется.
 */
@Component
public class PipelineWorker {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineWorker.class);
    private static final long IDLE_MS = 1000;

    private final JobCommandService commands;
    private final StageRunner runner;
    private final DocprocProperties props;
    private final String node = UUID.randomUUID().toString().substring(0, 8);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<UUID, String> active = new ConcurrentHashMap<>();
    private ExecutorService pool;

    public PipelineWorker(JobCommandService commands, StageRunner runner, DocprocProperties props) {
        this.commands = commands;
        this.runner = runner;
        this.props = props;
    }

    /** Старт после готовности приложения. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!props.workersEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        pool = Executors.newFixedThreadPool(props.workers());
        for (int i = 0; i < props.workers(); i++) {
            String name = node + "-" + i;
            pool.submit(() -> loop(name));
        }
        LOG.info("docproc workers started: {}", props.workers());
    }

    /** Один проход: взять задание и выполнить (используется циклом и тестами). */
    public boolean once(String worker) {
        Optional<Job> job = commands.claim(worker);
        if (job.isEmpty()) {
            return false;
        }
        Job j = job.get();
        active.put(j.id(), worker);
        try {
            runner.run(j);
        } finally {
            active.remove(j.id());
        }
        return true;
    }

    private void loop(String worker) {
        while (running.get()) {
            try {
                if (!once(worker)) {
                    Thread.sleep(IDLE_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                LOG.error("worker {} error {}", worker, e.getClass().getSimpleName(), e);
                sleepQuietly();
            }
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(IDLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Продление аренды выполняемых заданий. */
    @Scheduled(fixedDelayString = "${app.docproc.lease-renew:30s}")
    public void renewLeases() {
        active.forEach((id, worker) -> {
            if (!commands.extend(id, worker)) {
                LOG.warn("lease lost job={}", id);
            }
        });
    }

    /** Остановка. */
    @PreDestroy
    public void stop() throws InterruptedException {
        running.set(false);
        if (pool != null) {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
