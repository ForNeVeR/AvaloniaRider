package me.fornever.avaloniarider.bson

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import de.undercouch.bson4jackson.BsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BsonStreamReader(private val typeRegistry: Map<UUID, Class<*>>, private val stream: DataInputStream) {
    companion object {
        private val logger = logger<BsonStreamReader>()
        private val objectMapper = JsonMapper.builder(BsonFactory())
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // The messages are Kotlin data classes with `val` properties and defaults for every parameter, so Jackson
            // creates them with the no-arg constructor and has to write the final fields. Jackson 3 disables that by
            // default and then silently leaves every property at its default value.
            .configure(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS, true)
            .build()
    }

    private fun readBytes(count: Int) = ByteBuffer.allocate(count).order(ByteOrder.nativeOrder()).apply {
        stream.readFully(array())
    }

    fun readMessage(): Any? {
        val infoBuffer = readBytes(20)
        val header = MessageHeader.fromBytes(infoBuffer.array())
        logger.trace { "Received header: $header" }

        val body = readBytes(header.length)
        val type = typeRegistry[header.typeId] ?: run {
            logger.warn("Cannot find type with id ${header.typeId}")
            return null
        }
        logger.trace { "Received message type: ${type.simpleName}" }
        ByteArrayInputStream(body.array()).use { bodyStream ->
            return objectMapper.readValue(bodyStream, type)
        }
    }
}
