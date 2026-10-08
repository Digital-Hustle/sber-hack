package ru.proxodnaya.access.features.pass.model;

/**
 * Кто проходит — для экрана киоска.
 *
 * @param id         рабочий
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 */
public record WorkerPreview(String id, String fullName, String position, String contractor) {

    /**
     * Превью из профиля.
     *
     * @param profile профиль people
     * @return превью для киоска
     */
    public static WorkerPreview of(WorkerProfile profile) {
        return new WorkerPreview(profile.workerId(), profile.fullName(), profile.position(), profile.contractor());
    }
}
