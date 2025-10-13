plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "app.trainy.de.stations"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass = "app.trainy.de.stations.MainKt"
}

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.csv)
    implementation(libs.slf4j.simple)
    implementation(libs.stdx.envconf)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}