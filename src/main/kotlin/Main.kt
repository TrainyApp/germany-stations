package app.trainy.de.stations

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import app.trainy.germanystations.db.Database
import app.trainy.operator.client.CachingOperator
import app.trainy.operator.client.operator.db.ris.PrivateRISOperator
import app.trainy.operator.client.operator.db.ris.RISOperator
import app.trainy.operator.client.operator.db.vendo.loadICEDescriptions
import app.trainy.operator.client.operator.db.zugportal.types.loadFVLines
import ch.qos.logback.classic.Logger
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.http.ContentType.Application.TYPE
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
import org.slf4j.LoggerFactory
import java.sql.SQLException


suspend fun main() {
    initializeLogging()
    // Load stations by loading class
    @Suppress("UnusedExpression")
    StationStore

    val (driver, database) = initializePostgres()
    migrate(database, driver)

    val risOperator = PrivateRISOperator(
        baseUrl = Config.RIS_API_URL,
        clientId = Config.DB_CLIENT_ID,
        apiKey = Config.DB_API_KEY,
        fvLines = loadFVLines(),
        iceDescriptions = loadICEDescriptions(),
        cache = CachingOperator.defaultCache
    )

    embeddedServer(Netty, port = Config.PORT, host = Config.HOST) {
        module(database, risOperator)
    }.start(wait = true)
}


fun initializeLogging() {
    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger
    if (rootLogger == null) {
        LoggerFactory.getLogger("Trainy").warn("Could not set log level due to different logging engine being used")
        return
    }
    rootLogger.level = Config.LOGLEVEL
}

private fun Application.module(database: Database, risOperator: RISOperator) {
    install(ContentNegotiation) {
        json(contentType = ContentType(TYPE, "vnd.de.db.ris+json"))
        json()
    }
    install(Resources)


    routing {
        RISStationsProxy(database, risOperator)

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
        username = Config.POSTGRES_USER
        password = Config.POSTGRES_PASSWORD
    }
    val datasource = HikariDataSource(config)
    val driver = datasource.asJdbcDriver()
    return driver to Database(driver)
}

suspend fun migrate(database: Database, driver: SqlDriver) {
    val currentVersion = try {
        database.migrationsQueries.currentVersion().executeAsOne().current_version ?: 0L
    } catch (_: SQLException) {
        0L
    }
    val newVersion = Database.Schema.version
    if (newVersion == currentVersion) return
    if (newVersion < currentVersion) error("Unsupported db schema version: $currentVersion")
    Database.Schema.migrate(
        driver,
        currentVersion,
        newVersion
    ).await()
    database.migrationsQueries.applyMigration(newVersion)
}
