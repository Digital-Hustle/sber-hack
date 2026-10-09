// Сервис с БД: PostgreSQL + Liquibase (миграции применяются при старте), доступ — Spring JdbcClient.
// jOOQ codegen из живой БД не используем: docker build должен собираться с чистого клона без БД (ADR-014).
plugins {
    id("documents.service")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")
}

// Интеграционные тесты ходят в настоящий PostgreSQL (CI: service container, локально — свой).
tasks.withType<Test>().configureEach {
    environment("DB_NAME", project.name)
}
