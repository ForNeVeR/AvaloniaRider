package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.application
import com.intellij.util.ui.AsyncProcessIcon
import com.jetbrains.rd.util.lifetime.Lifetime
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.controlmessages.UpdateXamlResultMessage
import me.fornever.avaloniarider.idea.concurrency.adviseOnUiThread
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings
import me.fornever.avaloniarider.plainTextToHtml
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController.Status
import me.fornever.avaloniarider.previewer.nonTransparent
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

class BitmapPreviewEditorComponent(
    lifetime: Lifetime,
    private val controller: AvaloniaPreviewerSessionController,
    settings: AvaloniaSettings
) : JPanel() {
    companion object {
        private val logger = Logger.getInstance(BitmapPreviewEditorComponent::class.java)
    }

    private val mainScrollView = JBScrollPane().apply {
        border = null
    }
    private val frameBufferView = lazy {
        PreviewImageView(lifetime, controller, settings)
    }
    private val spinnerView = lazy { AsyncProcessIcon.Big("Loading") }
    private val terminatedView = lazy { JLabel("Previewer has been terminated") }

    private var status: Status = Status.Idle

    init {
        layout = BorderLayout()
        add(mainScrollView, BorderLayout.CENTER)

        controller.status.adviseOnUiThread(lifetime, ::handleStatus)
        controller.updateXamlResult.adviseOnUiThread(lifetime, ::handleXamlResult)
        controller.criticalError.adviseOnUiThread(lifetime, ::handleCriticalError)

        controller.frame.adviseOnUiThread(lifetime) { frame ->
            if (nonTransparent(frame)) { // TODO[F]: Remove after fix of https://github.com/AvaloniaUI/Avalonia/issues/4264
                if (!handleFrame(frame))
                    controller.acknowledgeFrame(frame)
            } else {
                controller.acknowledgeFrame(frame)
            }
        }
    }

    private fun handleStatus(newStatus: Status) {
        application.assertIsDispatchThread()
        mainScrollView.viewport.view = when (newStatus) {
            Status.Idle, Status.Connecting -> spinnerView.value
            Status.Working -> frameBufferView.value
            Status.XamlError -> frameBufferView.value
            Status.Suspended -> spinnerView.value
            Status.Terminated -> terminatedView.value
            is Status.NoOutputAssembly -> null // handled by AvaloniaPreviewEditorBase
            else -> {
                logger.error("Unhandled status message: $newStatus")
                null
            }
        }

        status = newStatus
        logger.info("Status: $status")
    }

    /**
     * Returns whether it has got the ownership of the frame. If it isn't, then the caller should call acknowledgeFrame.
     */
    private fun handleFrame(frame: FrameMessage): Boolean {
        application.assertIsDispatchThread()
        if (status != Status.Working) {
            logger.warn("Had to skip a frame because it came during status $status")
            return false
        }

        val frameBuffer = frameBufferView.value
        if (frame.height <= 0 || frame.width <= 0) {
            frameBuffer.resetImage()
            return false
        }

        frameBuffer.render(frame)
        return true
    }

    private fun handleXamlResult(message: UpdateXamlResultMessage) {
        val errorMessage =
            if (message.exception == null && message.error == null) ""
            else {
                val buffer = StringBuilder()
                val exception = message.exception
                if (exception != null) {
                    val exceptionType = exception.exceptionType ?: "Error"
                    buffer.append(exceptionType)
                    if (exception.lineNumber != null || exception.linePosition != null) {
                        buffer.append(" at ").append(when {
                            exception.lineNumber != null && exception.linePosition != null ->
                                "${exception.lineNumber}:${exception.linePosition}"
                            exception.lineNumber != null -> "${exception.lineNumber}"
                            else -> "position ${exception.linePosition}"
                        })
                    }

                    if (exception.message != null) {
                        buffer.append(": ").append(exception.message)
                    }
                }
                if (message.error != null) {
                    if (message.exception != null) buffer.append("\n")
                    buffer.append(message.error)
                }
                buffer.toString()
            }

        if (errorMessage.isNotEmpty())
            logger.warn("XAML update error: $errorMessage")
    }

    private fun handleCriticalError(error: Throwable) {
        terminatedView.value.text = "Previewer has been terminated: ${error.localizedMessage}".plainTextToHtml()
    }
}
