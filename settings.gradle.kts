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
            version("shadow", "9.0.2")
            version("velocity", "3.4.0-SNAPSHOT")

            version("jackson", "2.17.3")
            version("jakarta-annotation", "2.1.1")

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

            library("jackson-core",        "com.fasterxml.jackson.core", "jackson-core").versionRef("jackson")
            library("jackson-annotations", "com.fasterxml.jackson.core", "jackson-annotations").versionRef("jackson")
            library("jackson-databind",    "com.fasterxml.jackson.core", "jackson-databind").versionRef("jackson")
            library("jackson-datatype-jsr310", "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310").versionRef("jackson")

            library("jackson-databind-nullable", "org.openapitools", "jackson-databind-nullable").version("0.2.6")
            library("jakarta-annotation-api", "jakarta.annotation", "jakarta.annotation-api").versionRef("jakarta-annotation")


            plugin("micronaut.application", "io.micronaut.application").versionRef("micronaut")
            plugin("micronaut.aot", "io.micronaut.aot").versionRef("micronaut")
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
        }
    }
}
include("backend")
include("java-client")
include("velocity-plugin")
