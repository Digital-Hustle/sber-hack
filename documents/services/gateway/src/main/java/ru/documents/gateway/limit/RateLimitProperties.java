package ru.documents.gateway.limit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Лимиты частоты запросов по IP клиента (FR-06, SECURITY T8).
 *
 * @param enabled включены ли лимиты
 * @param rules   правила: метод, шаблон пути, сколько запросов за окно
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, List<Rule> rules) {

    /** Значения по умолчанию: лимиты включены, правил нет. */
    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * Одно правило.
     *
     * @param id      имя правила (ключ корзины и метка в логах)
     * @param method  HTTP-метод
     * @param path    шаблон пути (PathPattern, например /intake/api/v1/applicant/applications/{id}/documents)
     * @param limit   сколько запросов разрешено за окно
     * @param window  окно
     */
    public record Rule(String id, String method, String path, int limit, Duration window) {
    }
}
