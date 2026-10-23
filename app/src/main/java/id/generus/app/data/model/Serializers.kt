package id.generus.app.data.model

import id.generus.app.core.parseInstant
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** timestamptz dari Postgres ("2026-10-22T06:00:00+00:00") ↔ Instant. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("IsoInstant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant = parseInstant(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}

/** date dari Postgres ("2026-10-22") ↔ LocalDate. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("IsoDate", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString().take(10))
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
}

typealias IsoInstant = @Serializable(InstantSerializer::class) Instant
typealias IsoDate = @Serializable(LocalDateSerializer::class) LocalDate
