@file:OptIn(ExperimentalSerializationApi::class)

package app.trainy.de.stations

import app.trainy.germanystations.db.Database
import app.trainy.germanystations.db.Metropolises
import app.trainy.germanystations.db.Names
import app.trainy.germanystations.db.Positions
import app.trainy.germanystations.db.Stations
import app.trainy.operator.client.operator.db.ris.KeyType
import app.trainy.operator.client.operator.db.ris.RISOperator
import app.trainy.operator.client.operator.db.ris.RISStations
import app.trainy.types.data.Position
import io.ktor.client.call.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

fun Route.RISStationsProxy(database: Database, risOperator: RISOperator) {
    get<RISStations.StopPlaces.Specific.Keys> {

    }

    get<RISStations.StopPlaces.ByKey> { (key, keyType) ->
        val cache = database.cacheQueries.getStationsByKeyFromCache(keyType.name, key).executeAsList()
        if (!cache.isEmpty()) {
            val risCache = cache.map { station ->
                val names = database.cacheQueries.getNamesFromStation(station.internalStationId).executeAsList()
                val position = database.cacheQueries.getPositionFromStation(station.internalStationId).executeAsOneOrNull()
                val metropolis = database.cacheQueries.getMetropolisFromStation(station.internalStationId).executeAsList()
                station.toRISStation(
                    names,
                    metropolis,
                    position
                )
            }
            call.respond(risCache)
        } else {
            val stations = risOperator.stationByKeyRequest(key, keyType).body<StationSearchResponse>().stopPlaces;
            call.respond(stations)
            stations.forEach {station ->
                station.insert(database, keyType, key)
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

fun Stations.toRISStation(names: List<Names>, metropolis: List<Metropolises>, position: Positions?): RISStation =
    RISStation(
        evaNumber = evaNumber,
        stationID = stationID,
        names = names.associate { it.languageCode to it.toRISName() },
        metropolis = metropolis.associate { it.countryCode to it.name },
        availableTransports = availableTransports.toList(),
        replacementTransportsAvailable = replacementTransportsAvailable,
        availablePhysicalTransports = availablePhysicalTransports.toList(),
        transportAssociations = transportAssociations.toList(),
        countryCode = countryCode,
        postalCode = postalCode,
        state = state,
        municipalityKey = municipalityKey,
        timeZone = timeZone,
        position = position?.toRISPosition()
    )

fun Names.toRISName(): Name = Name(
    nameLong = nameLong,
    nameShort = nameShort,
    nameLocal = nameLocal,
    speechLong = speechLong,
    speechShort = speechShort,
    symbol = symbol,
    synonyms = synonyms?.toList()
)

fun Positions.toRISPosition(): Position = Position(
    latitude = latitude.toDouble(),
    longitude = longitude.toDouble()
)

@Serializable
data class StationSearchResponse(
    val stopPlaces: List<RISStation>
)

@Serializable
data class Name(
    val nameLong: String,
    val nameShort: String? = null,
    val nameLocal: String? = null,
    val speechLong: String? = null,
    val speechShort: String? = null,
    val symbol: String? = null,
    val synonyms: List<String>? = null
)

@JsonIgnoreUnknownKeys
@Serializable
data class RISStation(
    val evaNumber: String,
    val stationID: String? = null,
    val names: Map<String, Name>,
    val metropolis: Map<String, String>? = null,
    val availableTransports: List<String>,
    val replacementTransportsAvailable: Boolean? = null,
    val availablePhysicalTransports: List<String>,
    val transportAssociations: List<String>,
    val countryCode: String,
    val postalCode: String? = null,
    val state: String? = null,
    val municipalityKey: String? = null,
    val timeZone: String,
    val position: Position? = null
) {
    fun insert(database: Database, keyType: KeyType, key: String) {
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
        val dbStation = database.cacheQueries.getStationFromEvaNumber(evaNumber).executeAsList()
        if (dbStation.isEmpty()) {
            println("oops, station not found after insert: $evaNumber")
            return
        }
        if (position != null) {
            database.cacheQueries.insertPositionIfNotExists(
                internalStationId = dbStation[0].internalStationId,
                latitude = position.latitude.toBigDecimal(),
                longitude = position.longitude.toBigDecimal()
            )
        }
        metropolis?.entries?.forEach { (countryCode, name) ->
            database.cacheQueries.insertMetropolisIfNotExists(
                internalStationId = dbStation[0].internalStationId,
                countryCode = countryCode,
                name = name
            )
        }
        names.entries.forEach { (languageCode, name) ->
            database.cacheQueries.insertNameIfNotExists(
                internalStationId = dbStation[0].internalStationId,
                languageCode = languageCode,
                nameLocal = name.nameLocal,
                nameLong = name.nameLong,
                nameShort = name.nameShort,
                speechLong = name.speechLong,
                speechShort = name.speechShort,
                symbol = name.symbol,
                synonyms = name.synonyms?.toTypedArray(),
            )
        }
        database.cacheQueries.insertCachedByKey(
            keyType = keyType.name,
            key = key,
            internalStationId = dbStation[0].internalStationId,
        )
    }
}
