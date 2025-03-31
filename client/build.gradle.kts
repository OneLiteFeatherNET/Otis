group = "net.theevilreaper"
version = "1.0.0"


dependencies {
    implementation(mn.jackson.core)
    implementation(mn.jackson.databind)
    implementation(mn.jackson.datatype.jdk8)

    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation(mn.junit.jupiter.api)
    testImplementation("org.jetbrains:annotations:26.0.2")
    testImplementation(mn.jackson.core)
    testImplementation(mn.jackson.databind)
    testImplementation(mn.jackson.datatype.jdk8)
    testRuntimeOnly(mn.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}