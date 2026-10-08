package ru.proxodnaya.access.features.pass.model;

/**
 * Ответ на скан QR: сразу решение (отказ) или челлендж живости.
 *
 * @param decision  решение, если челлендж не нужен
 * @param challenge челлендж, если QR прошёл проверки
 * @param worker    кто проходит (для челленджа)
 */
public record ScanOutcome(PassOutcome decision, PendingAttempt challenge, WorkerPreview worker) {

    /**
     * Сразу решение.
     *
     * @param decision решение
     * @return ответ без челленджа
     */
    public static ScanOutcome decided(PassOutcome decision) {
        return new ScanOutcome(decision, null, null);
    }

    /**
     * Челлендж.
     *
     * @param challenge попытка с челленджем
     * @param worker    кто проходит
     * @return ответ с челленджем
     */
    public static ScanOutcome challenge(PendingAttempt challenge, WorkerPreview worker) {
        return new ScanOutcome(null, challenge, worker);
    }

    /**
     * Нужен ли челлендж.
     *
     * @return {@code true}, если киоск должен снять кадры
     */
    public boolean isChallenge() {
        return challenge != null;
    }
}
