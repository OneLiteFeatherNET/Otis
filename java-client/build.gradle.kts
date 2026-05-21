plugins {
    id("org.openapi.generator") version "7.22.0"
    `maven-publish`
}

val outDir = layout.buildDirectory.dir("generated/openapi")

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.core)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotation.api)
}

// OpenAPI Generator configuration
openApiGenerate {
    generatorName.set("java")
    library.set("native")
    inputSpec.set("$projectDir/specs/otis-api-1.0.1.yml")
    outputDir.set(outDir.get().asFile.absolutePath)
    apiPackage.set("net.onelitefeather.otis.client.api")
    invokerPackage.set("net.onelitefeather.otis.client.invoker")
    modelPackage.set("net.onelitefeather.otis.client.model")
    additionalProperties.set(
        mapOf(
            "dateLibrary" to "java8",
            "useTags" to "true",
            "interfaceOnly" to "true",
            "useSpringBoot3" to "false",
            "useSpring4Annotations" to "false",
            "useJakartaEe" to "true",
            "serializationLibrary" to "gson",
            "artifactId" to "otis-client",
            "groupId" to "net.onelitefeather.otis",
            "artifactVersion" to rootProject.version.toString(),
            "buildTool" to "gradle",
            "generateBuilders" to "true",

            "useJava8Time" to "true",
            "hideGenerationTimestamp" to "true"
        )
    )
    globalProperties.set(
        mapOf(
            "apiTests" to "false",
            "modelTests" to "false"
        )
    )
}

sourceSets.named("main") {
    java.srcDir(outDir.map { it.dir("src/main/java") })
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.named("sourcesJar") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks {
    javadoc {
        options {
            (this as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        version = rootProject.version as String
        artifactId = "otis-client"
        groupId = rootProject.group as String
        pom {
            name = "Otis Client API"
            description = "The client for Otis."
            url = "https://github.com/OneLiteFeatherNET/Otis"
            licenses {
                license {
                    name = "The Apache License, Version 2.0"
                    url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            developers {
                developer {
                    id = "theevilreaper"
                    name = "Steffen Wonning"
                    email = "steffenwx@gmail.com"
                }
                developer {
                    id = "themeinerlp"
                    name = "Phillipp Glanz"
                    email = "p.glanz@madfix.me"
                }
            }
            scm {
                connection = "scm:git:git://github.com:OneLiteFeatherNET/Otis.git"
                developerConnection = "scm:git:ssh://git@github.com:OneLiteFeatherNET/Otis.git"
                url = "https://github.com/OneLiteFeatherNET/Otis"
            }
        }
    }

    repositories {
        maven {
            authentication {
                credentials(PasswordCredentials::class) {
                    // Those credentials need to be set under "Settings -> Secrets -> Actions" in your repository
                    username = System.getenv("ONELITEFEATHER_MAVEN_USERNAME")
                    password = System.getenv("ONELITEFEATHER_MAVEN_PASSWORD")
                }
            }

            name = "OneLiteFeatherRepository"
            val releasesRepoUrl = uri("https://repo.onelitefeather.dev/onelitefeather-releases")
            val snapshotsRepoUrl = uri("https://repo.onelitefeather.dev/onelitefeather-snapshots")
            url =
                if (version.toString().contains("SNAPSHOT") || version.toString().contains("BETA") || version.toString()
                        .contains("ALPHA")
                ) snapshotsRepoUrl else releasesRepoUrl
        }
    }
}
