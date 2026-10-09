package ru.proxodnaya.people;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Сервис people: люди, устройства, зоны и проходные. Владелец: B1.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PeopleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PeopleApplication.class, args);
    }
}
