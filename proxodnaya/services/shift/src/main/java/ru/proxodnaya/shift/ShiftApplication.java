package ru.proxodnaya.shift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Сервис shift: смены, интервалы присутствия, аналитика. Владелец: B3.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ShiftApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiftApplication.class, args);
    }
}
