package me.fornever.avaloniarider.bson

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.threading.coroutines.launch
import de.undercouch.bson4jackson.BsonFactory
import kotlinx.coroutines.asCoroutineDispatcher
import me.fornever.avaloniarider.controlmessages.AvaloniaMessage
import java.io.OutputStream
import java.util.*
import java.util.concurrent.Executors

class BsonStreamWriter(
    private val lifetime: Lifetime,
    private val typeRegistry: Map<Class<*>, UUID>,
    private val output: OutputStream,
    private val port: Int) {
    companion object {
        private val logger = logger<BsonStreamWriter>()
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
        lifetime.launch(writerThread) {
            logger.trace { "(port $port) Start sending a message…" }

            val body = objectMapper.writeValueAsBytes(message)
            val header = MessageHeader(body.size, typeRegistry.getValue(message.javaClass))
            output.write(header.toByteArray())
            logger.trace { "(port $port) Sent header $header" }

            output.write(body)
            output.flush()
            logger.trace { "(port $port) Sent message $message" }
        }
    }
}
