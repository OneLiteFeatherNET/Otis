plugins {
    alias(libs.plugins.micronaut.application)
    alias(libs.plugins.micronaut.aot)
}

version = "0.1"
group = "net.theevilreaper"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor(mn.micronaut.serde.processor)
    annotationProcessor(mn.micronaut.http.validation)
    annotationProcessor(mn.micronaut.openapi)

    compileOnly(mn.micronaut.openapi.annotations)

    implementation(mn.micronaut.http.validation)
    implementation(mn.micronaut.runtime)
    implementation(mn.validation)
    implementation(mn.micronaut.http.client.jdk)
    implementation(mn.micronaut.cache.caffeine)
    implementation(mn.micronaut.data.spring.jpa)
    implementation(mn.micronaut.serde.jackson)
    implementation(mn.micronaut.hibernate.jpa)
    implementation(mn.micronaut.hibernate.validator)
    implementation(mn.micronaut.data.tx.hibernate)
    implementation(mn.micronaut.jdbc.hikari)
    implementation(mn.mariadb.java.client)
    implementation(mn.postgresql)
    implementation(mn.h2)
    implementation(mn.snakeyaml)
   // implementation(mn.log4j)
    implementation(mn.logback.core)
    implementation(mn.logback.classic)
   // implementation(mn.slf4j.api)
   // implementation(mn.slf4j.simple)
    implementation(mn.jackson.core)
    implementation(mn.jackson.databind)
    implementation(mn.jackson.datatype.jsr310)

    testImplementation(mn.micronaut.test.rest.assured)
    testImplementation(mn.junit.jupiter.api)
    testImplementation(mn.junit.jupiter.params)
    testRuntimeOnly(mn.junit.jupiter.engine)
}


application {
    mainClass = "net.theevilreaper.OtisApplication"
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("net.theevilreaper.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading = false
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"
        options.forkOptions.jvmArgs =
            listOf("-Dmicronaut.openapi.views.spec=rapidoc.enabled=true,openapi-explorer.enabled=true,swagger-ui.enabled=true,swagger-ui.theme=flattop")
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("PASSED", "SKIPPED", "FAILED")
        }
    }
}
