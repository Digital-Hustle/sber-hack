package ru.proxodnaya.people.features.device.model;

import java.time.Instant;

import ru.proxodnaya.people.features.worker.api.WorkerCard;

/**
 * Итог активации телефона.
 *
 * @param worker     рабочий (для карточки, которую телефон кэширует)
 * @param deviceId   привязанное устройство
 * @param serverTime время сервера (телефон сверяет часы для QR)
 */
public record Activation(WorkerCard worker, String deviceId, Instant serverTime) {
}
