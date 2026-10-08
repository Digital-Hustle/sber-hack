package ru.proxodnaya.people.api.constants;

/**
 * Пути API people (gateway добавляет префикс {@code /people}).
 */
public final class PathConstants {

    public static final String API_V1 = "/api/v1";
    public static final String WORKERS = API_V1 + "/workers";
    public static final String WORKER_ID = "/{id}";
    public static final String BLOCK = "/{id}/block";
    public static final String UNBLOCK = "/{id}/unblock";

    private PathConstants() {
    }
}
