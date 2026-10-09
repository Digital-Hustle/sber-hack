package ru.proxodnaya.shift.features.stream.command.service;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ru.proxodnaya.shift.config.properties.StreamProperties;
import ru.proxodnaya.shift.features.stream.model.StreamEvent;
import ru.proxodnaya.shift.features.stream.model.StreamKind;

/**
 * Подписчики SSE и кольцевой буфер последних событий (SH-08). {@code Last-Event-ID} другой эпохи (до рестарта)
 * не досылается — клиент перечитывает сводку REST-запросом.
 */
@Component
public class SseHub {

    private static final Logger LOG = LoggerFactory.getLogger(SseHub.class);
    private static final String SEPARATOR = "-";

    private final Set<Subscriber> subscribers = new CopyOnWriteArraySet<>();
    private final Deque<StreamEvent> recent = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();
    private final String epoch;
    private final StreamProperties props;

    public SseHub(StreamProperties props, Clock clock) {
        this.props = props;
        this.epoch = Long.toString(clock.millis(), Character.MAX_RADIX);
    }

    /**
     * Подписка.
     *
     * @param kind        поток
     * @param workerId    для WORKER — чьи события, иначе {@code null}
     * @param lastEventId заголовок {@code Last-Event-ID} или {@code null}
     * @return emitter для ответа контроллера
     */
    public SseEmitter subscribe(StreamKind kind, String workerId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(props.maxAge().toMillis());
        Subscriber subscriber = new Subscriber(kind, workerId, emitter);
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> {
            subscribers.remove(subscriber);
            emitter.complete();
        });
        emitter.onError(error -> subscribers.remove(subscriber));
        try {
            emitter.send(SseEmitter.event().comment("connected").reconnectTime(props.heartbeat().toMillis()));
            for (StreamEvent event : missed(lastEventId)) {
                if (subscriber.wants(event)) {
                    emitter.send(frame(event));
                }
            }
            subscribers.add(subscriber);
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
        return emitter;
    }

    /**
     * Публикует событие подписчикам и в буфер.
     *
     * @param name     имя события SSE
     * @param kind     поток
     * @param workerId адресат для WORKER
     * @param data     тело
     */
    public void publish(String name, StreamKind kind, String workerId, Object data) {
        StreamEvent event = new StreamEvent(epoch + SEPARATOR + sequence.incrementAndGet(), name, kind, workerId, data);
        synchronized (recent) {
            recent.addLast(event);
            while (recent.size() > props.buffer()) {
                recent.removeFirst();
            }
        }
        for (Subscriber subscriber : subscribers) {
            if (subscriber.wants(event)) {
                send(subscriber, frame(event));
            }
        }
    }

    /**
     * Пульс: комментарий раз в {@code heartbeat}, заодно отсекает отвалившихся клиентов.
     */
    @Scheduled(fixedRateString = "${shift.stream.heartbeat}")
    public void heartbeat() {
        for (Subscriber subscriber : subscribers) {
            send(subscriber, SseEmitter.event().comment("hb"));
        }
    }

    /**
     * Сколько сейчас подписчиков (для метрик и тестов).
     *
     * @return число подписчиков
     */
    public int size() {
        return subscribers.size();
    }

    private List<StreamEvent> missed(String lastEventId) {
        if (lastEventId == null || !lastEventId.startsWith(epoch + SEPARATOR)) {
            return List.of();
        }
        long last;
        try {
            last = Long.parseLong(lastEventId.substring(epoch.length() + 1));
        } catch (NumberFormatException error) {
            return List.of();
        }
        synchronized (recent) {
            return recent.stream().filter(event -> number(event) > last).toList();
        }
    }

    private long number(StreamEvent event) {
        return Long.parseLong(event.id().substring(epoch.length() + 1));
    }

    private void send(Subscriber subscriber, SseEmitter.SseEventBuilder frame) {
        try {
            synchronized (subscriber) {
                subscriber.emitter().send(frame);
            }
        } catch (IOException | IllegalStateException error) {
            LOG.debug("SSE: клиент отключился: {}", error.getMessage());
            subscribers.remove(subscriber);
        }
    }

    private static SseEmitter.SseEventBuilder frame(StreamEvent event) {
        return SseEmitter.event().id(event.id()).name(event.name()).data(event.data(), MediaType.APPLICATION_JSON);
    }

    private record Subscriber(StreamKind kind, String workerId, SseEmitter emitter) {

        boolean wants(StreamEvent event) {
            return event.kind() == kind && (kind == StreamKind.SITUATION || workerId.equals(event.workerId()));
        }
    }
}
