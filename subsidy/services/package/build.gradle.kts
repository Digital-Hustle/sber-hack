plugins {
    id("subsidy.jooq")
}

dependencies {
    implementation(libs.tika.core)
    implementation(libs.pdfbox)
    implementation(libs.imageio.jpeg)
    implementation(libs.imageio.tiff)
    implementation(libs.poi.ooxml)
    implementation(libs.commons.compress)
}
