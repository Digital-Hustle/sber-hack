package ru.documents.intake.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки приёма (infra/.env).
 *
 * @param maxMb             максимальный размер файла, МБ
 * @param maxPages          максимум страниц PDF/TIFF
 * @param maxFiles          максимум файлов в заявке
 * @param imageMinPx        минимальная короткая сторона изображения
 * @param filesKey          ключ AES-256-GCM (base64)
 * @param pepper            перец для хэшей ключа заявки и кода возврата
 * @param resumeMaxAttempts попыток ввода кода возврата
 * @param publicUrl         адрес фронта для ссылки-ключа
 * @param uploadsPerMinute  загрузок в минуту на заявку
 */
@ConfigurationProperties("app.intake")
public record IntakeProperties(int maxMb, int maxPages, int maxFiles, int imageMinPx, String filesKey, String pepper,
                               int resumeMaxAttempts, String publicUrl, int uploadsPerMinute) {
}
