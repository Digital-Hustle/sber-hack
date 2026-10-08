package ru.proxodnaya.access.features.pass.gateway;

/**
 * Клиент к people: профиль доступа рабочего (статус, допуски, зоны).
 * Таймаут 1.5 с, circuit breaker, fail-closed.
 */
public interface PeopleClient {

    /**
     * @param workerId id рабочего
     * @return профиль доступа или null если не найден
     */
    AccessProfile getAccessProfile(String workerId);
}
