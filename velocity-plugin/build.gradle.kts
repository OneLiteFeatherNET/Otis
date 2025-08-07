plugins {
    id("java")
}

dependencies {
    implementation(project(":client"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}