group = "net.onelitefeather"
version = "1.0.0"

dependencies {
    annotationProcessor(libs.velocity.api)
    implementation(project(":client"))

    compileOnly(libs.velocity.api)
}
