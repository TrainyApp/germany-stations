package app.trainy.de.stations

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

    embeddedServer(Netty, port = Config.PORT, host = Config.HOST) {
        module()
    }.start(wait = true)
}

private fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(Resources)


    routing {
        post<Stations> {
            val (ids) = call.receive<StationsRequest>()

            call.respond(ids.mapNotNull {
                StationStore.resolve(it) ?: StationStore.resolve(it.substringBeforeLast(':'))
            })
        }
    }
}
