package ru.proxodnaya.people.shared;

/**
 * Ограничения полей (совпадают с check-ограничениями в миграциях).
 */
public final class Limits {

    /** Длина текстового поля карточки. */
    public static final int TEXT = 200;

    /** Длина адреса. */
    public static final int ADDRESS = 300;

    /** Длина списков в карточке (зоны, допуски). */
    public static final int ITEMS = 100;

    /** Снимков лица на эталон. */
    public static final int FACE_FRAMES = 3;

    /** Длина снимка JPEG в base64 (≈ 2 МБ, как FACE_MAX_FRAME_BYTES в face). */
    public static final int PHOTO = 2_800_000;

    private Limits() {
    }
}
