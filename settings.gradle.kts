pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "germany-stations"