package ru.documents.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Рабочее место оператора: очередь, карточка заявки, правки, решения, история, отчёты.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ReviewApplication {

    /**
     * Запуск.
     *
     * @param args аргументы
     */
    public static void main(String[] args) {
        SpringApplication.run(ReviewApplication.class, args);
    }
}
