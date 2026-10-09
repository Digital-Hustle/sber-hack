package ru.documents.review;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Каждый тестовый класс получает свою пустую схему: честная проверка «пустая БД → нули». */
public abstract class AbstractIntegrationTest {

    private static final String SCHEMA = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @DynamicPropertySource
    static void schema(DynamicPropertyRegistry r) {
        r.add("spring.datasource.hikari.connection-init-sql",
                () -> "create schema if not exists " + SCHEMA + "; set search_path to " + SCHEMA);
        r.add("spring.liquibase.default-schema", () -> SCHEMA);
    }
}
