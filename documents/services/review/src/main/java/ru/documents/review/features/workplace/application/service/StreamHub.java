package ru.documents.review.features.workplace.application.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.documents.review.features.workplace.model.StreamEvent;

/**
 * SSE операторов (FR-57): queue.changed и document.stage. Подписки — только открытые соединения процесса.
 */
@Component
public class StreamHub {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private static final long HEARTBEAT_MS = 20_000;

    private final List<SseEmitter> subs = new CopyOnWriteArrayList<>();

    /**
     * Подписаться.
     *
     * @return поток
     */
    public SseEmitter subscribe() {
        SseEmitter e = new SseEmitter(TIMEOUT_MS);
        subs.add(e);
        Runnable drop = () -> subs.remove(e);
        e.onCompletion(drop);
        e.onTimeout(drop);
        e.onError(t -> drop.run());
        send(e, "ready", Map.of());
        return e;
    }

    /**
     * Разослать событие.
     *
     * @param ev событие
     */
    public void publish(StreamEvent ev) {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationId", ev.applicationId().toString());
        if (ev.documentId() != null) {
            data.put("documentId", ev.documentId().toString());
        }
        if (ev.stage() != null) {
            data.put("stage", ev.stage());
        }
        subs.forEach(e -> send(e, ev.name(), data));
        if (!"queue.changed".equals(ev.name())) {
            Map<String, Object> q = Map.of("applicationId", ev.applicationId().toString());
            subs.forEach(e -> send(e, "queue.changed", q));
        }
    }

    /** Пинг, чтобы прокси не закрывали соединение. */
    @Scheduled(fixedDelay = HEARTBEAT_MS)
    public void heartbeat() {
        subs.forEach(e -> send(e, "ping", Map.of()));
    }

    private void send(SseEmitter e, String event, Object data) {
        try {
            e.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            subs.remove(e);
            e.complete();
        }
    }
}
