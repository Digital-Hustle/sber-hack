package ru.documents.docproc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Сервис разбора документов: очередь в БД, OCR, классификация и извлечение через модель, проверки.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DocprocApplication {

    /**
     * Запуск.
     *
     * @param args аргументы
     */
    public static void main(String[] args) {
        SpringApplication.run(DocprocApplication.class, args);
    }
}
