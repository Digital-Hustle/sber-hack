plugins { id("subsidy.library") }
dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    api("org.springframework.kafka:spring-kafka")
    api(libs.mapstruct)
    api(libs.uuid.creator)
    api("org.springframework.boot:spring-boot-starter-jdbc")
}
