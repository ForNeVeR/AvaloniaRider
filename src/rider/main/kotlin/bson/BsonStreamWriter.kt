package me.fornever.avaloniarider.bson

import com.fasterxml.jackson.databind.ObjectMapper
import com.jetbrains.rd.framework.util.startAsync
import com.jetbrains.rd.platform.util.launchIOBackground
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.trace
import de.undercouch.bson4jackson.BsonFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import me.fornever.avaloniarider.controlmessages.AvaloniaMessage
import java.io.OutputStream
import java.util.*
import java.util.concurrent.Executors

class BsonStreamWriter(
    private val lifetime: Lifetime,
    private val typeRegistry: Map<Class<*>, UUID>,
    private val output: OutputStream) {
    companion object {
        private val logger = getLogger<BsonStreamWriter>()
        private val objectMapper = ObjectMapper(BsonFactory())
    }

    private val writerThread = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
    init {
        lifetime.onTermination {
            writerThread.close()
        }
    }

    fun startSendMessage(message: AvaloniaMessage) {
        @Suppress("BlockingMethodInNonBlockingContext")
        lifetime.launchIOBackground {
            val body = objectMapper.writeValueAsBytes(message)
            val header = MessageHeader(body.size, typeRegistry.getValue(message.javaClass))
            output.write(header.toByteArray())
            logger.trace { "Sent header $header" }

            output.write(body)
            output.flush()
            logger.trace { "Sent message $message" }
        }
    }
}
