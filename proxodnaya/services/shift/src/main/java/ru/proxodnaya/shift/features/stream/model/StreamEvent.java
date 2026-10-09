package ru.proxodnaya.shift.features.stream.model;

/**
 * Событие потока.
 *
 * @param id       {@code <эпоха процесса>-<номер>} — для {@code Last-Event-ID}
 * @param name     имя события SSE ({@code attempt.created}, {@code presence.changed}, …)
 * @param kind     в какой поток
 * @param workerId адресат для WORKER-потока, иначе {@code null}
 * @param data     тело (JSON)
 */
public record StreamEvent(String id, String name, StreamKind kind, String workerId, Object data) {
}
