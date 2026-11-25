package com.syll.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** iOS `LenientEnum`: an unknown raw value decodes to [fallback], so a new server value never fails a response. */
open class LenientEnumSerializer<T : Enum<T>>(
    name: String,
    private val values: Array<T>,
    private val raw: (T) -> String,
    private val fallback: T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): T {
        val value = decoder.decodeString()
        return values.firstOrNull { raw(it) == value } ?: fallback
    }
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(raw(value))
}

/** ISO 8601 with or without fractional seconds, any offset (iOS `.withInternetDateTime[, .withFractionalSeconds]`). */
object Iso8601InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Iso8601Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (e: DateTimeParseException) {
            throw SerializationException("Invalid ISO 8601 date: $text")
        }
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }
}
