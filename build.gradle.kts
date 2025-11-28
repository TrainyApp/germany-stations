import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "app.trainy.de.stations"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://europe-west3-maven.pkg.dev/mik-music/trainy-dependencies")
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
    implementation(libs.stdx.envconf)
    implementation(libs.operatorclient)
    implementation(libs.sqldelight.jdbc.driver)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.logback)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_24
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
}

sqldelight {
    databases {
        create("Database", Action {
            packageName = "app.trainy.germanystations.db"
            dialect(libs.sqldelight.postgresql.dialect)

            schemaOutputDirectory = file("src/main/sqldelight/databases")
            verifyMigrations = true
            deriveSchemaFromMigrations = true


        })
    }
}