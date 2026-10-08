package ru.proxodnaya.access.shared;

/**
 * Действие для проверки живости. Сервер выбирает случайное, киоск показывает подсказку и присылает кадры.
 */
public enum ChallengeAction {
    TURN_LEFT("turn-left", "Медленно поверните голову влево"),
    TURN_RIGHT("turn-right", "Медленно поверните голову вправо"),
    BLINK("blink", "Моргните два раза"),
    NOD("nod", "Кивните");

    private final String wire;
    private final String textRu;

    ChallengeAction(String wire, String textRu) {
        this.wire = wire;
        this.textRu = textRu;
    }

    /**
     * Значение в контракте фронта ({@code turn-left | turn-right | blink | nod}).
     *
     * @return строка для поля {@code challenge.kind}
     */
    public String wire() {
        return wire;
    }

    /**
     * Подсказка для экрана киоска.
     *
     * @return текст по-русски
     */
    public String textRu() {
        return textRu;
    }
}
