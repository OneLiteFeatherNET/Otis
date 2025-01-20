rootProject.name = "otis"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://eldonexus.de/repository/maven-public/")
    }
}

plugins {
    id("io.micronaut.platform.catalog") version "4.4.5"
}


dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("micronaut", "4.4.5")

            plugin("micronaut.application", "io.micronaut.application").versionRef("micronaut")
            plugin("micronaut.aot", "io.micronaut.aot").versionRef("micronaut")
        }
    }
}
include("backend")
include("client")
