rootProject.name = "otis"

plugins {
    id("io.micronaut.platform.catalog") version "4.5.4"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
    versionCatalogs {
        create("libs") {
            version("micronaut", "4.5.4")
            version("annotations", "26.0.2")
            version("shadow", "9.0.0")
            version("velocity", "3.4.0-SNAPSHOT")

            library(
                "jetbrains.annotations",
                "org.jetbrains",
                "annotations"
            ).versionRef("annotations")
            library(
                "velocity.api",
                "com.velocitypowered",
                "velocity-api"
            ).versionRef("velocity")

            plugin("micronaut.application", "io.micronaut.application").versionRef("micronaut")
            plugin("micronaut.aot", "io.micronaut.aot").versionRef("micronaut")
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
        }
    }
}
include("backend")
include("client")
include("velocity-plugin")