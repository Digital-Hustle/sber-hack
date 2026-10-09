package ru.documents.intake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Сервис приёма: заявки, анкета → набор документов, приём файлов и серверные проверки.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IntakeApplication {

    /**
     * Запуск.
     *
     * @param args аргументы
     */
    public static void main(String[] args) {
        SpringApplication.run(IntakeApplication.class, args);
    }
}
