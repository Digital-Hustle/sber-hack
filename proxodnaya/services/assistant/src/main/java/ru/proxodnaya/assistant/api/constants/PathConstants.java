package ru.proxodnaya.assistant.api.constants;

/**
 * Пути API assistant (gateway: {@code /assistant/api/v1/...} → {@code /api/v1/...}).
 */
public final class PathConstants {

    public static final String API = "/api/v1";
    public static final String ASSISTANT = API + "/assistant";
    public static final String ASK = "/ask";
    public static final String STATUS = "/status";

    private PathConstants() {
    }
}
