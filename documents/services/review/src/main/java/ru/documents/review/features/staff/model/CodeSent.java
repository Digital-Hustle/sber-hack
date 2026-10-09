package ru.documents.review.features.staff.model;

/**
 * Ответ на запрос кода — одинаковый, есть такая почта или нет (не раскрываем, кто сотрудник).
 *
 * @param sentTo      куда отправили (почта со скрытой серединой)
 * @param expiresInSec сколько действует код
 * @param resendInSec через сколько можно запросить снова
 */
public record CodeSent(String sentTo, long expiresInSec, long resendInSec) {
}
