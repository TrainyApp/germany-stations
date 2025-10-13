package app.trainy.de.stations

import kotlinx.serialization.*
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.NumberFormat
import java.util.*

@Serializable
data class ZHVStation(
    @SerialName("SeqNo")
    val sequenceNumber: Int,
    @SerialName("Type")
    val type: String,
    @SerialName("DHID")
    val dhid: String,
    @SerialName("Parent")
    val parent: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Latitude")
    val latitude: DEDouble,
    @SerialName("Longitude")
    val longitude: DEDouble,
    @SerialName("MunicipalityCode")
    val municipalityCode: String,
    @SerialName("Municipality")
    val municipality: String,
    @SerialName("DistrictCode")
    val districtCode: String,
    @SerialName("District")
    val district: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Authority")
    val authority: String,
    @SerialName("DelfiName")
    val delfiName: String,
    @SerialName("THID")
    val thid: String,
    @SerialName("TariffProvider")
    val tariffProvider: String,
    @SerialName("LastOperationDate")
    val lastOperationDate: String,
    @SerialName("SEV")
    val hasRailReplacements: DEBoolean
) {
    @OptIn(ExperimentalSerializationApi::class)
    companion object {
        private val csv = Csv {
            ignoreUnknownColumns = true
            hasHeaderRecord = true
            escapeChar = '\\'
            delimiter = ';'
        }

        fun parse(text: String) = csv.decodeFromString<List<ZHVStation>>(text)

        fun load(): List<ZHVStation> {
            val text = ClassLoader.getSystemResource("zhv.csv").readText()
            return parse(text)
        }
    }
}

typealias DEDouble = @Serializable(with = DEDoubleSerializer::class) Double

object DEDoubleSerializer : KSerializer<Double> {
    private val format = NumberFormat.getNumberInstance(Locale.GERMANY)

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DEDouble", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Double) =
        encoder.encodeString(format.format(value))

    override fun deserialize(decoder: Decoder): Double =
        format.parse(decoder.decodeString()).toDouble()
}

typealias DEBoolean = @Serializable(with = DEBooleanSerializer::class) Boolean

object DEBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DEBoolean", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Boolean) = when (value) {
        true -> encoder.encodeString("ja")
        false -> encoder.encodeString("nein")
    }

    override fun deserialize(decoder: Decoder): Boolean = when (val value = decoder.decodeString()) {
        "ja" -> true
        "nein" -> false
        else -> throw IllegalArgumentException(
            "Can't deserialize boolean from $value"
        )
    }
}
