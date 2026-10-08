plugins {
    id("proxodnaya.service")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}
