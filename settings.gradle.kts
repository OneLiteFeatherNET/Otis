rootProject.name = "otis"

plugins {
    id("io.micronaut.platform.catalog") version "4.6.2"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
    versionCatalogs {
        create("libs") {
            version("micronaut", "4.6.2")
            version("annotations", "26.1.0")
            version("shadow", "9.4.0")
            version("velocity", "3.4.0")

            version("jackson", "2.21.2")
            version("jakarta-annotation", "3.0.0")

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

            library("jackson-bom",  "com.fasterxml.jackson", "jackson-bom").versionRef("jackson")
            library("jackson-core",        "com.fasterxml.jackson.core", "jackson-core").withoutVersion()
            library("jackson-annotations", "com.fasterxml.jackson.core", "jackson-annotations").withoutVersion()
            library("jackson-databind",    "com.fasterxml.jackson.core", "jackson-databind").withoutVersion()
            library("jackson-datatype-jsr310", "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310").withoutVersion()

            library("jackson-databind-nullable", "org.openapitools", "jackson-databind-nullable").version("0.2.10")
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
