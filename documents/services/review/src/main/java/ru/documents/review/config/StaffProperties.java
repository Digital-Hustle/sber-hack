package ru.documents.review.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Сотрудники и вход по коду из письма (FE-UI-04).
 *
 * @param adminUrl    адрес Admin REST realm (…/admin/realms/documents), ходим service account svc-review
 * @param mailFrom    отправитель писем (общий ящик сервера)
 * @param loginUrl    адрес входа — для приглашения
 * @param logCodes    без SMTP писать код в лог (только разработка и стенд до настройки почты)
 * @param codeTtl     сколько действует код (это же шаг TOTP в Keycloak)
 * @param resendAfter не чаще одного письма за этот интервал
 * @param mode        {@code dev} — тестовый стенд (быстрый вход, код 000000), иначе боевой
 */
@ConfigurationProperties("app.staff")
public record StaffProperties(String adminUrl, String mailFrom, String loginUrl, boolean logCodes, Duration codeTtl,
                              Duration resendAfter, String mode) {

    /** Тестовый режим стенда ({@code APP_MODE=dev}). */
    public boolean testMode() {
        return "dev".equalsIgnoreCase(mode);
    }
}
