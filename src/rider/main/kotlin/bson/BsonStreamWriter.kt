package me.fornever.avaloniarider.bson

import com.fasterxml.jackson.databind.ObjectMapper
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import de.undercouch.bson4jackson.BsonFactory
import me.fornever.avaloniarider.controlmessages.AvaloniaMessage
import java.io.OutputStream
import java.util.*

class BsonStreamWriter(private val typeRegistry: Map<Class<*>, UUID>, private val output: OutputStream) {
    companion object {
        private val logger = getLogger<BsonStreamWriter>()
        private val objectMapper = ObjectMapper(BsonFactory())
    }

    fun sendMessage(message: AvaloniaMessage) {
        val body = objectMapper.writeValueAsBytes(message)
        val header = MessageHeader(body.size, typeRegistry.getValue(message.javaClass))
        output.write(header.toByteArray())
        val threadInfo = Thread.currentThread().id
        logger.info { "<$threadInfo> Sent header $header" }

        output.write(body)
        output.flush()
        logger.info { "<$threadInfo> Sent message $message" }
    }
}
