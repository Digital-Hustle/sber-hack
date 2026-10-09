package ru.documents.review.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки рабочего места оператора.
 *
 * @param intakeUrl            адрес intake
 * @param docprocUrl           адрес docproc
 * @param expiringDays         горизонт отчёта «скоро истекают», дней
 * @param fieldReviewThreshold поле ниже этой уверенности поднимается наверх как «перепроверить»
 * @param ticketTtl            срок жизни билета SSE
 * @param zone                 часовой пояс площадки (для «сегодня»)
 */
@ConfigurationProperties("app.review")
public record ReviewProperties(String intakeUrl, String docprocUrl, int expiringDays, double fieldReviewThreshold,
                               Duration ticketTtl, String zone) {
}
