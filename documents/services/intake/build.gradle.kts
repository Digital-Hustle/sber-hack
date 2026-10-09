plugins { id("documents.db") }
dependencies {
    implementation(libs.tika.core)
    implementation(libs.pdfbox)
    implementation("org.springframework.kafka:spring-kafka")
}
