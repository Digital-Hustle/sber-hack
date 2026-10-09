package ru.proxodnaya.people.features.staff.gateway;

/**
 * Письма персоналу.
 */
public interface StaffMailer {

    /**
     * Код входа.
     *
     * @param email   адрес
     * @param code    6 цифр
     * @param minutes сколько минут действует
     */
    void loginCode(String email, String code, long minutes);

    /**
     * Приглашение в кабинет.
     *
     * @param email   адрес
     * @param name    имя
     * @param role    название роли
     * @param invitedBy кто пригласил
     */
    void invitation(String email, String name, String role, String invitedBy);
}
