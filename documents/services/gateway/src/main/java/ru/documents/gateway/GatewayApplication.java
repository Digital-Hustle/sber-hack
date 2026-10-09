package ru.documents.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа: маршруты /intake, /docproc, /review → сервисы; JWT Keycloak; /internal наружу закрыт.
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * Запуск.
     *
     * @param args аргументы
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
