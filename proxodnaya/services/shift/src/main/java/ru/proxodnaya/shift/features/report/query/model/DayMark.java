package ru.proxodnaya.shift.features.report.query.model;

/**
 * Код дня в табеле.
 */
public enum DayMark {
    /** Был на объекте. */
    PRESENT("Т"),
    /** Плановая смена прошла, а прохода не было. */
    ABSENT("Н"),
    /** Ни смены, ни прохода — или смена ещё впереди. */
    NONE("");

    private final String code;

    DayMark(String code) {
        this.code = code;
    }

    /**
     * Буква для бухгалтерии.
     *
     * @return Т, Н или пусто
     */
    public String code() {
        return code;
    }
}
