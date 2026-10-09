plugins {
    id("subsidy.jooq")
}

dependencies {
    implementation(project(":libs:llm"))
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)
    implementation(libs.tika.core)
}
