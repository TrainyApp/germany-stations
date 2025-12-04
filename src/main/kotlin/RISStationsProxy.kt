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
import app.trainy.operator.client.operator.db.ris.StationKeySearchRequest
import app.trainy.operator.client.operator.db.ris.StationKeys
import app.trainy.types.data.Position
import io.ktor.client.call.*
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

fun Route.RISStationsProxy(database: Database, risOperator: RISOperator) {
    get<RISStations.StopPlaces.Specific.Keys> { route ->
        val evaNumber = route.specific.evaNumber
        val cache = database.keyQueries.getKeys(evaNumber).executeAsList()
        if (cache.isEmpty()) {
            val ris = risOperator.getStationKeys(evaNumber)
            call.respond(ris)
            ris.keys.forEach {
                database.keyQueries.insertKey(evaNumber, it.type.name, it.key)
            }
        } else {
            call.respond(
                StationKeys(
                    cache.mapNotNull {
                        it.keyType.toKeyType()?.let { type -> StationKeys.Key(type = type, key = it.key) }
                    }
                )
            )
        }
    }

    get<RISStations.StopPlaces.ByKey> { (key, keyType) ->
        val cache = getStationFromCache(database, keyType.name, key)
        if (cache != null) {
            call.respond(cache)
        } else {
            val stations = risOperator.stationByKeyRequest(key, keyType).body<StationSearchResponse>().stopPlaces
            call.respond(stations)
            stations.forEach { station ->
                station.insert(database)
            }
            database.bykeyQueries.insertCachedByKey(keyType.name, key, stations.map { it.evaNumber }.toTypedArray())
        }
    }

    post<RISStations.StopPlaces.ByKeys> {
        val (keyType, keys) = call.receive<StationKeySearchRequest>()
        val cachedKeys = keys.map {
            Pair(it.key, getStationFromCache(database, keyType.name, it.key))
        }
        val fetched = cachedKeys.filter { (_, stations) ->
            stations == null
        }.map { (key, _) ->
            Pair(
                key,
                risOperator.stationByKeyRequest(key, keyType.toRISKeyType())
                    .body<StationSearchResponse>().stopPlaces
            )
        }
        val result = (cachedKeys + fetched).filter { (_, stations) ->
            stations != null
        }.associateBy(
            { (key, _) -> key },
            { (_, stations) -> stations!! }
        )
        call.respond(result)
        fetched.forEach { (key, stations) ->
            stations.forEach { station ->
                station.insert(database)
            }
            database.bykeyQueries.insertCachedByKey(keyType.name, key, stations.map { it.evaNumber }.toTypedArray())
        }
    }

    get<RISStations.StopPlaces.ByName> { route ->
        val ris = risOperator.searchStationsRequest(
            query = route.query,
            limit = route.limit ?: 10,
            groupBy = route.groupBy,
            sortBy = route.sortBy
        )
        call.respond(ris.body<app.trainy.operator.client.operator.db.ris.StationSearchResponse>())
    }

    get<RISStations.StopPlaces.ByPosition> { route ->
        val ris = risOperator.searchStationsNearbyRequest(
            latitude = route.latitude,
            longitude = route.longitude,
            radius = route.radius,
            groupBy = route.groupBy,
            limit = route.limit ?: 10
        )
        call.respond(ris.body<app.trainy.operator.client.operator.db.ris.StationSearchResponse>())
    }
}

fun String.toKeyType(): KeyType? =
    when (this) {
        "IFOPT" -> KeyType.IFOPT
        "EVA" -> KeyType.EVA
        "RL100" -> KeyType.RL100
        "RL100_ALTERNATIVE" -> KeyType.RL100_ALTERNATIVE
        "EPA" -> KeyType.EPA
        "STADA" -> KeyType.STADA
        "IBNR" -> KeyType.IBNR
        "EBHF" -> KeyType.EBHF
        "UIC" -> KeyType.UIC
        "PLC" -> KeyType.PLC
        else -> null
    }


fun getStationFromCache(database: Database, keyType: String, key: String): List<RISStation>? {
    val cache = database.bykeyQueries.getStationsByKeyFromCache(keyType, key).executeAsList()
    if (!cache.isEmpty()) {
        val risCache = cache.map { station ->
            val names = database.stationQueries.getNamesFromStation(station.evaNumber).executeAsList()
            val position = database.stationQueries.getPositionFromStation(station.evaNumber).executeAsOneOrNull()
            val metropolis = database.stationQueries.getMetropolisFromStation(station.evaNumber).executeAsList()
            station.toRISStation(
                names,
                metropolis,
                position
            )
        }
        return risCache
    }
    return null
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

fun StationKeySearchRequest.KeyType.toRISKeyType(): KeyType =
    when (this) {
        StationKeySearchRequest.KeyType.EVA -> KeyType.EVA
        StationKeySearchRequest.KeyType.STADA -> KeyType.STADA
        StationKeySearchRequest.KeyType.RL100 -> KeyType.RL100
    }


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
    fun insert(database: Database) {
        database.stationQueries.insertStationIfNotExists(
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
        if (position != null) {
            database.stationQueries.insertPositionIfNotExists(
                evaNumber = evaNumber,
                latitude = position.latitude.toBigDecimal(),
                longitude = position.longitude.toBigDecimal()
            )
        }
        metropolis?.entries?.forEach { (countryCode, name) ->
            database.stationQueries.insertMetropolisIfNotExists(
                evaNumber = evaNumber,
                countryCode = countryCode,
                name = name
            )
        }
        names.entries.forEach { (languageCode, name) ->
            database.stationQueries.insertNameIfNotExists(
                evaNumber = evaNumber,
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
    }
}
