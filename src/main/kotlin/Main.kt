package app.trainy.de.stations

import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import app.trainy.germanystations.db.Database
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.routing

fun main() {
    // Load stations by loading class
    @Suppress("UnusedExpression")
    StationStore

    val (driver, database) = initializePostgres()

    embeddedServer(Netty, port = Config.PORT, host = Config.HOST) {
        module(database)
    }.start(wait = true)
}

private fun Application.module(database: Database) {
    install(ContentNegotiation) {
        json()
    }
    install(Resources)


    routing {
        RISStationsProxy(database)

        post<Stations> {
            val (ids) = call.receive<StationsRequest>()

            call.respond(ids.mapNotNull {
                StationStore.resolve(it) ?: StationStore.resolve(it.substringBeforeLast(':'))
            })
        }
    }
}

fun initializePostgres(): Pair<JdbcDriver, Database> {
    val config = HikariConfig().apply {
        jdbcUrl = Config.POSTGRES_URI
    }
    val datasource = HikariDataSource(config)
    val driver = datasource.asJdbcDriver()
    return driver to Database(driver)
}
