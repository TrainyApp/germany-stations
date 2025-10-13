package app.trainy.de.stations

import kotlinx.serialization.Serializable

@Serializable
data class StationsRequest(val ifoptIds: List<String>)

object StationStore {
    private val delegate = load()
    private fun load(): Map<String, ZHVStop> {
        val allStations = ZHVStation.load()

        return allStations.asSequence()
            .filter { it.parent == it.dhid }.associate { it.parent to it.toZHVStop()}
    }

    fun resolve(ifopt: String) = delegate[ifopt]
}
