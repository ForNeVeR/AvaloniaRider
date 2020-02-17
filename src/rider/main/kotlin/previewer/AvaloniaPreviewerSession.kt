package me.fornever.avaloniarider.previewer

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.Signal
import com.jetbrains.rider.util.idea.application
import me.fornever.avaloniarider.bson.BsonStreamReader
import me.fornever.avaloniarider.bson.BsonStreamWriter
import me.fornever.avaloniarider.controlmessages.*
import java.awt.Dimension
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
    private val avaloniaMessages: AvaloniaMessages,
    private val serverSocket: ServerSocket,

    private val outputBinaryPath: Path,
    private val xamlFile: VirtualFile) {

    companion object {
        private val logger = getLogger<AvaloniaPreviewerSession>()
    }

    private val lifetimeDefinition = parentLifetime.createNested()
    private val lifetime = lifetimeDefinition.lifetime
    private lateinit var window: AvaloniaPreviewerWindow

    val requestViewportSize = Signal<RequestViewportResizeMessage>()
    val frame = Signal<FrameMessage>()

    fun start() {
        // TODO[F]: Properly declare the scheduler for all the socket actions
        startListeningThread()
        window = createWindow()
    }

    private lateinit var reader: BsonStreamReader
    private lateinit var writer: BsonStreamWriter

    private fun startListeningThread() = Thread {
        try {
            serverSocket.use { serverSocket ->
                val socket = serverSocket.accept()
                serverSocket.close()
                socket.use {
                    socket.getInputStream().use {
                        DataInputStream(it).use { input ->
                            socket.getOutputStream().use { output ->
                                attachVfsListener()
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

    private fun createWindow() = AvaloniaPreviewerWindow().apply {
        lifetime.onTermination {
            dispose()
        }
    }

    private fun attachVfsListener() {
        VirtualFileManager.getInstance().addAsyncFileListener(
            AsyncFileListener { events ->
                if (events.any { it.file == xamlFile && it is VFileContentChangeEvent }) {
                    onXamlChanged()
                }
                null
            },
            lifetime.createNestedDisposable("AvaloniaPreviewerSession"))
    }

    private fun onXamlChanged() {
        application.runReadAction {
            val document = FileDocumentManager.getInstance().getDocument(xamlFile) ?: return@runReadAction
            val xaml = document.text

            // TODO[F]: Make sure writer is used in a thread-safe manner
            writer.sendMessage(UpdateXamlMessage(xaml, outputBinaryPath.toString()))
        }
    }

    private fun handleMessage(message: AvaloniaMessage) {
        when (message) {
            is StartDesignerSessionMessage -> {
                onXamlChanged()
            }
            is UpdateXamlResultMessage -> message.error?.let {
                // TODO[F]: Show error message in the editor control
                logger.error { "Error from UpdateXamlResultMessage: $it" }
            }
            is RequestViewportResizeMessage -> {
                requestViewportSize.fire(message)

                UIUtil.invokeLaterIfNeeded {
                    window.size = Dimension(message.width.toInt(), message.height.toInt())
                }

                // TODO[F]: Properly send these from the editor control
                val dpi = 96.0
                writer.sendMessage(ClientRenderInfoMessage(dpi, dpi))
                writer.sendMessage(ClientViewportAllocatedMessage(message.width, message.height, dpi, dpi))
                writer.sendMessage(ClientSupportedPixelFormatsMessage(intArrayOf(1)))
            }
            is FrameMessage -> {
                frame.fire(message)

                UIUtil.invokeAndWaitIfNeeded(Runnable {
                    window.isVisible = true
                    window.size = Dimension(message.width, message.height)
                    window.drawFrame(message)
                })

                writer.sendMessage(FrameReceivedMessage(message.sequenceId))
            }
        }
    }
}
