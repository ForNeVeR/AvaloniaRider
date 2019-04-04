package me.fornever.avaloniarider.bson

import com.fasterxml.jackson.databind.ObjectMapper
import com.jetbrains.rider.util.getLogger
import com.jetbrains.rider.util.info
import de.undercouch.bson4jackson.BsonFactory
import me.fornever.avaloniarider.AvaloniaMessage
import java.io.OutputStream

class BsonStreamWriter(private val output: OutputStream) {
    companion object {
        private val logger = getLogger<BsonStreamWriter>()
        private val objectMapper = ObjectMapper(BsonFactory())
    }

    fun sendMessage(message: AvaloniaMessage) {
        val header = MessageHeader(57, message.guid)
        output.write(header.toByteArray())
        logger.info { "Sent header $header" }

        output.write(objectMapper.writeValueAsBytes(message))
        logger.info { "Sent message $message" }
    }
}
