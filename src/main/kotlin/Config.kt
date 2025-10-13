package app.trainy.de.stations

import dev.schlaubi.envconf.Config as EnvironmentConfig

object Config : EnvironmentConfig() {
    val HOST by getEnv("::")
    val PORT by getEnv(8080, String::toInt)
}
