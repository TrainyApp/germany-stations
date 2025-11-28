package app.trainy.de.stations

import app.trainy.germanystations.db.Database
import app.trainy.operator.client.operator.db.ris.PrivateRISOperator
import app.trainy.operator.client.operator.db.ris.RISStations
import app.trainy.types.data.Position
import io.ktor.client.call.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

fun Route.RISStationsProxy(database: Database, risOperator: PrivateRISOperator) {
    get<RISStations.StopPlaces.Specific.Keys> {

    }

    get<RISStations.StopPlaces.ByKey> { (key, keyType) ->
        val cache = database.cacheQueries.getStationsByKeyFromCache(keyType.name, key).executeAsList()
        if (!cache.isEmpty()) {
            call.respond(cache)
        } else {
            val stations = risOperator.stationByKeyRequest(key, keyType).body<StationSearchResponse>().stopPlaces;
            call.respond(stations)
            stations.forEach {station ->
                station.insert(database)
            }
        }
    }

    get<RISStations.StopPlaces.ByName> {

    }

    get<RISStations.StopPlaces.ByPosition> {

    }

    get<RISStations.StopPlaces.ByKeys> {

    }
}

@Serializable
data class StationSearchResponse(
    val stopPlaces: List<RISStation>
)

@Serializable
data class Name(
    val nameLong: String,
    val nameShort: String,
    val nameLocal: String,
    val speechLong: String,
    val speechShort: String,
    val symbol: String,
    val synonyms: List<String>
)

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class RISStation(
    val evaNumber: String,
    val stationID: String,
    val names: Map<String, Name>,
    val metropolis: Map<String, String>,
    val availableTransports: List<String>,
    val replacementTransportsAvailable: Boolean,
    val availablePhysicalTransports: List<String>,
    val transportAssociations: List<String>,
    val countryCode: String,
    val postalCode: String,
    val state: String,
    val municipalityKey: String,
    val timeZone: String,
    val position: Position
) {
    fun insert(database: Database) {
        database.cacheQueries.insertStationIfNotExists(
            availableTransports = availableTransports.toTypedArray(),
            stationID = stationID,
            state = state,
            evaNumber = evaNumber,
            timeZone = timeZone,
            postalCode = postalCode,
            countryCode = countryCode,
            municipalityKey = municipalityKey,
            transportAssociations = transportAssociations.toTypedArray(),
            availablePhysicalTransports = availablePhysicalTransports.toTypedArray(),
            replacementTransportsAvailable = replacementTransportsAvailable,
        )
        val dbStation = database.cacheQueries.getStationFromEvaNumber(evaNumber).executeAsOneOrNull()
        if (dbStation == null) {
            println("oops, station not found after insert: $evaNumber")
            return
        }
        database.cacheQueries.insertPositionIfNotExists(
            internalStationId = dbStation.internalStationId,
            latitude = position.latitude.toString(),
            longitude = position.longitude.toString()
        )
        metropolis.entries.forEach { (countryCode, name) ->
            database.cacheQueries.insertMetropolisIfNotExists(
                internalStationId = dbStation.internalStationId,
                countryCode = countryCode,
                name = name
            )
        }
        names.entries.forEach { (languageCode, name) ->
            database.cacheQueries.insertNameIfNotExists(
                internalStationId = dbStation.internalStationId,
                languageCode = languageCode,
                nameLocal = name.nameLocal,
                nameLong = name.nameLong,
                nameShort = name.nameShort,
                speechLong = name.speechLong,
                speechShort = name.speechShort,
                symbol = name.symbol,
                synonyms = name.synonyms.toTypedArray(),
            )
        }
    }
}
