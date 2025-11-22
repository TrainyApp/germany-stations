package app.trainy.de.stations

import app.cash.sqldelight.db.SqlDriver
import app.trainy.germanystations.db.Database
import io.ktor.server.routing.Route
import app.trainy.operator.client.operator.db.ris.RISStations
import io.ktor.server.resources.get

fun Route.RISStationsProxy(database: Database) {
    get<RISStations.StopPlaces.Specific.Keys> {
        val stations = database.stationQueries.getstations().executeAsList()
    }

    get<RISStations.StopPlaces.ByKey> {

    }

    get<RISStations.StopPlaces.ByName> {

    }

    get<RISStations.StopPlaces.ByPosition> {

    }

    get<RISStations.StopPlaces.ByKeys> {

    }
}