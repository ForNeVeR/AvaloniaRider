package me.fornever.avaloniarider.previewer

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.Signal
import me.fornever.avaloniarider.bson.BsonStreamReader
import me.fornever.avaloniarider.bson.BsonStreamWriter
import me.fornever.avaloniarider.controlmessages.*
import java.io.DataInputStream
import java.net.ServerSocket
import java.nio.file.Path

/**
 * Avalonia previewer session.
 *
 * @param serverSocket server socket to connect to the previewer. Will be owned
 * by the session (i.e. it will manage the socket lifetime).
 */
class AvaloniaPreviewerSession(
    private val lifetime: Lifetime,
    private val serverSocket: ServerSocket,
    private val outputBinaryPath: Path) {

    companion object {
        private val logger = logger<AvaloniaPreviewerSession>()
    }

    private val sessionStartedSignal = Signal<StartDesignerSessionMessage>()
    private val htmlTransportStartedSignal = Signal<HtmlTransportStartedMessage>()
    private val requestViewportResizeSignal = Signal<RequestViewportResizeMessage>()
    private val frameSignal = Signal<FrameMessage>()
    private val updateXamlResultSignal = Signal<UpdateXamlResultMessage>()

    val sessionStarted: ISource<StartDesignerSessionMessage> = sessionStartedSignal
    val htmlTransportStarted: ISource<HtmlTransportStartedMessage> = htmlTransportStartedSignal
    val requestViewportResize: ISource<RequestViewportResizeMessage> = requestViewportResizeSignal
    val frame: ISource<FrameMessage> = frameSignal
    val updateXamlResult: ISource<UpdateXamlResultMessage> = updateXamlResultSignal

    private lateinit var reader: BsonStreamReader
    private lateinit var writer: BsonStreamWriter

    fun processSocketMessages() {
        val avaloniaMessages = AvaloniaMessages.getInstance()

        serverSocket.use { serverSocket ->
            val socket = serverSocket.accept().apply {
                lifetime.onTermination { close() }
            }
            serverSocket.close()
            socket.use {
                socket.getInputStream().use {
                    DataInputStream(it).use { input ->
                        socket.getOutputStream().use { output ->
                            writer = BsonStreamWriter(
                                lifetime,
                                avaloniaMessages.outgoingTypeRegistry,
                                output,
                                serverSocket.localPort
                            )
                            reader = BsonStreamReader(avaloniaMessages.incomingTypeRegistry, input)
                            while (!socket.isClosed) {
                                val message = reader.readMessage()
                                if (message == null) {
                                    logger.info("Message == null received, terminating the connection")
                                    return
                                }
                                handleMessage(message as AvaloniaMessage)
                            }
                        }
                    }
                }
            }
        }
    }

    fun sendClientSupportedPixelFormat() {
        writer.startSendMessage(ClientSupportedPixelFormatsMessage(intArrayOf(1)))
    }

    fun sendDpi(dpi: Double) {
        writer.startSendMessage(ClientRenderInfoMessage(dpi, dpi))
    }

    fun sendXamlUpdate(content: String, xamlFilePathInsideProject: String) {
        val message = UpdateXamlMessage(content, outputBinaryPath.toString(), xamlFilePathInsideProject)
        writer.startSendMessage(message)
    }

    fun sendFrameAcknowledgement(frame: FrameMessage) {
        writer.startSendMessage(FrameReceivedMessage(frame.sequenceId))
    }

    fun sendInputEventMessage(event: AvaloniaInputEventMessage) {
        writer.startSendMessage(event)
    }

    private fun handleMessage(message: AvaloniaMessage) {
        logger.trace { "Received message: $message" }
        when (message) {
            is StartDesignerSessionMessage -> sessionStartedSignal.fire(message)
            is HtmlTransportStartedMessage -> htmlTransportStartedSignal.fire(message)
            is UpdateXamlResultMessage -> {
                updateXamlResultSignal.fire(message)
                message.error?.let {
                    logger.info("Error from UpdateXamlResultMessage: $it")
                }
            }
            is RequestViewportResizeMessage -> requestViewportResizeSignal.fire(message)
            is FrameMessage -> frameSignal.fire(message)
            else -> logger.warn("Message ignored: $message")
        }
    }
}
