package app.trainy.de.stations

import ch.qos.logback.classic.Level
import dev.schlaubi.envconf.Config as EnvironmentConfig

object Config : EnvironmentConfig() {
    val HOST by getEnv("::")
    val PORT by getEnv(8080, String::toInt)
    val MONITORING_PORT by getEnv(9000, String::toInt)
    val POSTGRES_URI by getEnv("jdbc:postgresql://localhost:5432/germanystations")
    val POSTGRES_USER by getEnv("postgres")
    val POSTGRES_PASSWORD by getEnv("postgres")
    val RIS_API_URL by getEnv()
    val DB_CLIENT_ID by getEnv()
    val DB_API_KEY by getEnv()
    val LOGLEVEL by getEnv(Level.INFO, Level::valueOf)
}
