package me.fornever.avaloniarider.previewer

import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.Signal
import com.jetbrains.rd.util.trace
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
    parentLifetime: Lifetime,
    private val serverSocket: ServerSocket,
    private val outputBinaryPath: Path) {

    companion object {
        private val logger = getLogger<AvaloniaPreviewerSession>()
    }

    private val lifetimeDefinition = parentLifetime.createNested()
    private val lifetime = lifetimeDefinition.lifetime

    private val sessionStartedSignal = Signal<StartDesignerSessionMessage>()
    private val requestViewportResizeSignal = Signal<RequestViewportResizeMessage>()
    private val frameSignal = Signal<FrameMessage>()
    private val updateXamlResultSignal = Signal<UpdateXamlResultMessage>() // TODO[F]: Show error message in the editor control

    val sessionStarted: ISource<StartDesignerSessionMessage> = sessionStartedSignal
    val requestViewportResize: ISource<RequestViewportResizeMessage> = requestViewportResizeSignal
    val frame: ISource<FrameMessage> = frameSignal
    val updateXamlResult: ISource<UpdateXamlResultMessage> = updateXamlResultSignal

    fun start() {
        // TODO[F]: Properly declare the scheduler for all the socket actions
        startListeningThread()
    }

    private lateinit var reader: BsonStreamReader
    private lateinit var writer: BsonStreamWriter // TODO[F]: Separate writer thread for this socket

    private fun startListeningThread() = Thread {
        try {
            val avaloniaMessages = AvaloniaMessages.getInstance()

            serverSocket.use { serverSocket ->
                val socket = serverSocket.accept()
                serverSocket.close()
                socket.use {
                    socket.getInputStream().use {
                        DataInputStream(it).use { input ->
                            socket.getOutputStream().use { output ->
                                writer = BsonStreamWriter(avaloniaMessages.outgoingTypeRegistry, output)
                                reader = BsonStreamReader(avaloniaMessages.incomingTypeRegistry, input)
                                while (!socket.isClosed) {
                                    val message = reader.readMessage()
                                    if (message == null) {
                                        logger.info { "Message == null received, terminating the connection" }
                                        return@Thread
                                    }
                                    handleMessage(message as AvaloniaMessage)
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: Throwable) {
            logger.error("Error while listening to Avalonia designer socket", ex)
        } finally {
            lifetimeDefinition.terminate()
        }
    }.apply { start() }

    fun sendXamlUpdate(content: String) {
        // TODO[F]: Make sure writer is used in a thread-safe manner
        writer.sendMessage(UpdateXamlMessage(content, outputBinaryPath.toString()))
    }

    fun sendFrameAcknowledgement(frame: FrameMessage) {
        // TODO[F]: Should be asynchronous and on the common writer thread
        writer.sendMessage(FrameReceivedMessage(frame.sequenceId))
    }

    private fun handleMessage(message: AvaloniaMessage) {
        logger.trace { "Received message: $message" }
        when (message) {
            is StartDesignerSessionMessage ->
                sessionStartedSignal.fire(message)
            is UpdateXamlResultMessage -> {
                updateXamlResultSignal.fire(message)
                message.error?.let {
                    logger.error { "Error from UpdateXamlResultMessage: $it" }
                }
            }
            is RequestViewportResizeMessage -> {
                requestViewportResizeSignal.fire(message)

                // TODO[F]: Properly send these from the editor control
                val dpi = 96.0
                writer.sendMessage(ClientRenderInfoMessage(dpi, dpi))
                writer.sendMessage(ClientViewportAllocatedMessage(message.width, message.height, dpi, dpi))
                writer.sendMessage(ClientSupportedPixelFormatsMessage(intArrayOf(1)))
            }
            is FrameMessage -> {
                frameSignal.fire(message)
            }
        }
    }
}
