rootProject.name = "otis"

plugins {
    id("io.micronaut.platform.catalog") version "4.5.4"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            version("micronaut", "4.5.4")
            version("annotations", "26.0.2")

            library("jetbrains.annotations", "org.jetbrains", "annotations").versionRef("annotations")

            plugin("micronaut.application", "io.micronaut.application").versionRef("micronaut")
            plugin("micronaut.aot", "io.micronaut.aot").versionRef("micronaut")
        }
    }
}
include("backend")
include("client")
