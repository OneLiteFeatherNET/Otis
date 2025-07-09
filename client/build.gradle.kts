group = "net.onelitefeather"
version = "1.0.0"

dependencies {
    implementation(mn.jackson.core)
    implementation(mn.jackson.databind)
    implementation(mn.jackson.datatype.jdk8)

    compileOnly(libs.jetbrains.annotations)

    testImplementation(mn.junit.jupiter.api)
    testImplementation(mn.jackson.core)
    testImplementation(mn.jackson.databind)
    testImplementation(mn.jackson.datatype.jdk8)
    testRuntimeOnly(mn.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}