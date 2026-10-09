plugins { id("documents.db") }
dependencies {
    implementation(project(":libs:llm"))
    implementation("org.springframework.kafka:spring-kafka")
    implementation(libs.json.schema.validator)
}
