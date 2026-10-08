// Spring Boot сервис на servlet-стеке (все, кроме gateway).
plugins {
    id("proxodnaya.java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val catalog = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${catalog.findVersion("spring-cloud").get()}")
        // spring-cloud BOM тянет resilience4j 2.2.0, а стартер 2.4.0 ссылается на классы из spring6 2.4.0
        // (RxJava3OnClasspathCondition) — без выравнивания контекст не поднимается.
        mavenBom("io.github.resilience4j:resilience4j-bom:${catalog.findVersion("resilience4j").get()}")
    }
}

dependencies {
    implementation(project(":libs:common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation(lib("springdoc-webmvc"))
    implementation(lib("resilience4j-spring-boot"))
    implementation(lib("uuid-creator"))
    implementation(lib("mapstruct"))

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor(lib("mapstruct-processor"))
    annotationProcessor(lib("lombok-mapstruct-binding"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(lib("archunit"))
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "${project.name}.jar"
}
tasks.named<Jar>("jar") { enabled = false }

// OpenAPI-контракт: ./gradlew :svc:exportOpenApi → ../contracts/openapi/<svc>.json
tasks.register<Test>("exportOpenApi") {
    group = "verification"
    description = "Выгружает OpenAPI сервиса в contracts/openapi"
    val test = project.extensions.getByType<SourceSetContainer>()["test"]
    testClassesDirs = test.output.classesDirs
    classpath = test.runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*OpenApiContractIntegrationTest"); isFailOnNoMatchingTests = false }
    systemProperty("contract.dir", rootProject.file("../contracts/openapi").absolutePath)
    outputs.upToDateWhen { false }
}
