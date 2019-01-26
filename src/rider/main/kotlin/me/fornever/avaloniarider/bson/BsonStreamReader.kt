package me.fornever.avaloniarider.bson

import com.fasterxml.jackson.databind.ObjectMapper
import com.jetbrains.rider.util.getLogger
import com.jetbrains.rider.util.info
import com.jetbrains.rider.util.warn
import de.undercouch.bson4jackson.BsonFactory
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BsonStreamReader(private val typeRegistry: Map<UUID, Class<*>>, private val stream: DataInputStream) {
    companion object {
        private val logger = getLogger<BsonStreamReader>()
        private val objectMapper = ObjectMapper(BsonFactory())
    }

    private fun readBytes(count: Int) = ByteBuffer.allocate(count).order(ByteOrder.nativeOrder()).apply {
        stream.readFully(array())
    }

    fun readMessage(): Any? {
        val infoBuffer = readBytes(20)
        val header = MessageHeader.fromBytes(infoBuffer.array())
        logger.info { "Received header: $header" }
        val body = readBytes(header.length)
        val type = typeRegistry[header.typeId] ?: run {
            logger.warn { "Cannot find type with id ${header.typeId}" }
            return null
        }
        ByteArrayInputStream(body.array()).use { bodyStream ->
            return objectMapper.readValue(bodyStream, type)
        }
    }
}
