package ru.documents.intake.api.dto.request;

/**
 * Новая заявка.
 *
 * @param consentPd согласие на обработку ПДн (152-ФЗ)
 * @param contact   контакт для продолжения с другого устройства
 * @param demo      демо-заявка (помечается source=DEMO)
 */
public record CreateApplicationRq(boolean consentPd, Contact contact, Boolean demo) {

    /**
     * Контакт.
     *
     * @param phone телефон
     * @param email почта
     */
    public record Contact(String phone, String email) {
    }
}
