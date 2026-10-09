package ru.proxodnaya.people.features.worker.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Данные для создания или изменения рабочего; {@code null} в изменении — поле не трогаем.
 *
 * @param fullName   ФИО
 * @param position   должность
 * @param contractor подрядчик
 * @param zoneIds    зоны допуска (заменяют прежние)
 * @param permits    допуски со сроком (заменяют прежние)
 */
public record WorkerDraft(String fullName, String position, String contractor, Set<UUID> zoneIds, List<Permit> permits) {
}
