package ru.documents.docproc.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки docproc ({@code app.docproc.*}); пороги и веса калибруются на открытом наборе (EVAL.md).
 *
 * @param workersEnabled        запускать воркеры
 * @param workers               число воркеров
 * @param lease                 аренда задания
 * @param maxAttempts           попыток до FAILED
 * @param waitingModelRetry     пауза при недоступной модели
 * @param intakeUrl             intake
 * @param ocrUrl                ocr
 * @param ocrTimeout            тайм-аут ocr
 * @param classifyMinConfidence порог уверенности типа
 * @param fieldReviewThreshold  порог уверенности поля
 * @param ocrMinConfidence      средняя уверенность OCR (0..100), ниже — слабый текст
 * @param visionFallbackEnabled vision-путь при слабом OCR
 * @param minTextChars          минимум символов текста
 * @param blurMin               порог резкости
 * @param darkMax               порог яркости
 * @param glareMax              порог бликов
 * @param weightModel           вес самооценки модели
 * @param weightOcr             вес уверенности OCR
 * @param weightChecks          вес проверок
 * @param checksNotApplicable   оценка проверок, если они к полю не применимы
 * @param classifyTextChars     символов текста в классификацию
 * @param extractTextChars      символов текста в извлечение
 * @param zone                  часовой пояс для дат
 */
@ConfigurationProperties("app.docproc")
public record DocprocProperties(boolean workersEnabled, int workers, Duration lease, int maxAttempts,
                                Duration waitingModelRetry, String intakeUrl, String ocrUrl, Duration ocrTimeout,
                                double classifyMinConfidence, double fieldReviewThreshold, double ocrMinConfidence,
                                boolean visionFallbackEnabled, int minTextChars, double blurMin, double darkMax,
                                double glareMax, double weightModel, double weightOcr, double weightChecks,
                                Double checksNotApplicable, int classifyTextChars, int extractTextChars, String zone) {

    /** Значения по умолчанию для необязательных настроек. */
    public DocprocProperties {
        if (checksNotApplicable == null) {
            checksNotApplicable = 0.7;
        }
        if (zone == null || zone.isBlank()) {
            zone = "Europe/Moscow";
        }
    }
}
