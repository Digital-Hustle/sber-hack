plugins { id("documents.db") }
dependencies {
    implementation("org.springframework.kafka:spring-kafka")
    implementation(libs.poi.ooxml)
    implementation(libs.pdfbox)
    implementation("org.springframework.boot:spring-boot-starter-mail")
}
