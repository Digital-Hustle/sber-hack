package ru.proxodnaya.people.api.dto.response;

import java.time.Instant;

/**
 * Итог активации телефона.
 *
 * @param workerId   рабочий (едет в QR)
 * @param serverTime время сервера
 * @param card       карточка для работы без сети
 */
public record ActivationRs(String workerId, Instant serverTime, CardRs card) {

    /**
     * Карточка рабочего на телефоне.
     *
     * @param fullName   ФИО
     * @param position   должность
     * @param contractor подрядчик
     */
    public record CardRs(String fullName, String position, String contractor) {
    }
}
