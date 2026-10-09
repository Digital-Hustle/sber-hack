package ru.documents.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Управление сотрудниками (FE-UI-03): вход только по коду, коды выдаёт старший оператор.
 *
 * @param adminUrl адрес Admin REST realm (…/admin/realms/documents), ходим service account svc-review
 * @param issuer   подпись в приложении-аутентификаторе
 */
@ConfigurationProperties("app.staff")
public record StaffProperties(String adminUrl, String issuer) {
}
