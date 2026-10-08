package ru.proxodnaya.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Сервис access. Владелец: B2.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessApplication.class, args);
    }
}
