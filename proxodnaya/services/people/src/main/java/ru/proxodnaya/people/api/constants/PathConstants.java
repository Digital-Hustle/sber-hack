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
    public static final String INVITE = "/{id}/invite";
    public static final String DEVICES = "/{id}/devices";
    public static final String DEVICE = "/{id}/devices/{deviceId}";
    public static final String AUTH = API_V1 + "/auth";
    public static final String SITES = API_V1 + "/sites";
    public static final String ZONES = API_V1 + "/zones";
    public static final String CHECKPOINTS = API_V1 + "/checkpoints";
    public static final String INTERNAL = "/internal/people";
    public static final String ACCESS_PROFILE = "/workers/{id}/access-profile";
    public static final String INTERNAL_CHECKPOINT = "/checkpoints/{id}";
    public static final String ACTIVATE = "/worker/activate";

    private PathConstants() {
    }
}
