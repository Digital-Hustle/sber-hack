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

    private Limits() {
    }
}
