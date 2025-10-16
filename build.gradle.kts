subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    tasks {
        getByName<JavaCompile>("compileJava") {
            options.release.set(21)
            options.encoding = "UTF-8"
        }
        getByName<JacocoReport>("jacocoTestReport") {
            dependsOn(project.tasks.named("test"))
            reports {
                xml.required.set(true)
            }
        }
        getByName<Test>("test") {
            finalizedBy(project.tasks.named("jacocoTestReport"))
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}