plugins {
    id("java")
    alias(libs.plugins.shadow)
    `maven-publish`
}

dependencies {
    implementation(project(":client"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
}

publishing {
    publications.create<MavenPublication>("maven") {

        version = rootProject.version as String
        artifactId = "otis-plugin"
        groupId = rootProject.group as String
        pom {
            name = "Otis Velocity Plugin"
            description = "The client plugin for Otis."
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