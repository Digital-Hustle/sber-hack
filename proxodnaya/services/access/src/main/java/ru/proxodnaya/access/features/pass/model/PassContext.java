package ru.proxodnaya.access.features.pass.model;

import java.time.LocalDate;

import ru.proxodnaya.access.shared.Direction;

/**
 * Всё, что собрано для решения на шаге кадров.
 *
 * @param direction        направление
 * @param deviceId         устройство из QR
 * @param zoneId           зона проходной (пусто — не проверяем)
 * @param profile          профиль из people (пусто — рабочий не найден)
 * @param face             результат face
 * @param shift            активная смена (пусто — смены нет)
 * @param failedDependency имя упавшей зависимости (face | people | shift) или пусто
 * @param today            текущая дата на объекте
 */
public record PassContext(
        Direction direction,
        String deviceId,
        String zoneId,
        WorkerProfile profile,
        FaceResult face,
        ActiveShift shift,
        String failedDependency,
        LocalDate today
) {
}
