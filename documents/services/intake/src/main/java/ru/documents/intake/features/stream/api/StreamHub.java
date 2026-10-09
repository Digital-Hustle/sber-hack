package ru.documents.intake.features.stream.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Живые SSE-подписки кабинета (FR-12). Только доставка уведомлений «что-то изменилось» — данные клиент
 * перечитывает из API, поэтому потеря соединения ничего не теряет.
 */
@Component
public class StreamHub {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> subs = new ConcurrentHashMap<>();

    /**
     * Подписка.
     *
     * @param appId заявка
     * @return emitter
     */
    public SseEmitter subscribe(UUID appId) {
        SseEmitter e = new SseEmitter(TIMEOUT_MS);
        subs.computeIfAbsent(appId, k -> new CopyOnWriteArrayList<>()).add(e);
        Runnable drop = () -> subs.getOrDefault(appId, new CopyOnWriteArrayList<>()).remove(e);
        e.onCompletion(drop);
        e.onTimeout(drop);
        e.onError(t -> drop.run());
        send(e, "ready", Map.of("applicationId", appId.toString()));
        return e;
    }

    /**
     * Событие подписчикам заявки.
     *
     * @param appId заявка
     * @param event имя события
     * @param data  данные
     */
    public void publish(UUID appId, String event, Object data) {
        for (SseEmitter e : subs.getOrDefault(appId, List.of())) {
            send(e, event, data);
        }
    }

    /** Пинг, чтобы прокси не рвали соединение. */
    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        subs.forEach((app, list) -> list.forEach(e -> send(e, "ping", Map.of())));
    }

    private void send(SseEmitter e, String event, Object data) {
        try {
            e.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            e.complete();
        }
    }
}
