package me.fornever.avaloniarider.idea.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.controlmessages.UpdateXamlResultMessage
import me.fornever.avaloniarider.idea.concurrency.adviseOnUiThread
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController.Status
import me.fornever.avaloniarider.previewer.nonTransparent
import me.fornever.avaloniarider.previewer.renderFrame
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class BitmapPreviewEditorComponent(lifetime: Lifetime, controller: AvaloniaPreviewerSessionController) : JPanel() {
    companion object {
        private val logger = Logger.getInstance(BitmapPreviewEditorComponent::class.java)
    }

    private val frameBuffer = lazy { JLabel() }
    private val contentScrollView = lazy { JBScrollPane(frameBuffer.value) }
    private val spinnerView = lazy { AsyncProcessIcon.Big("Loading") }
    private val errorView = lazy { JLabel(AllIcons.General.Error) }
    private val terminatedView = lazy { JLabel("Previewer has been terminated") }

    private var visibleComponent: JComponent? = null
    private var status = Status.Idle

    init {
        layout = BorderLayout()

        controller.requestViewportResize.advise(lifetime) {
            // TODO[F]: Update the image size for the renderer (#40)
        }

        controller.status.adviseOnUiThread(lifetime, ::handleStatus)
        controller.updateXamlResult.adviseOnUiThread(lifetime, ::handleXamlResult)

        controller.frame.adviseOnUiThread(lifetime) { frame ->
            if (nonTransparent(frame)) // TODO[F]: Remove after fix of https://github.com/AvaloniaUI/Avalonia/issues/4264
                handleFrame(frame)
            controller.acknowledgeFrame(frame)
        }
    }

    private fun setCurrentView(view: JComponent) {
        if (visibleComponent == view) return
        visibleComponent?.isVisible = false
        if (!this.components.contains(view)) add(view, BorderLayout.CENTER)
        view.isVisible = true
    }

    private fun handleStatus(newStatus: Status) {
        application.assertIsDispatchThread()
        when (newStatus) {
            Status.Idle, Status.Connecting -> setCurrentView(spinnerView.value)
            Status.Working -> setCurrentView(contentScrollView.value)
            Status.XamlError -> setCurrentView(errorView.value)
            Status.Suspended -> setCurrentView(spinnerView.value)
            Status.Terminated -> setCurrentView(terminatedView.value)
        }

        status = newStatus
        logger.info("Status: $status")
    }

    private fun handleFrame(frame: FrameMessage) {
        application.assertIsDispatchThread()
        if (status != Status.Working) {
            logger.warn("Had to skip a frame because it came during status $status")
            return
        }

        val frameBuffer = frameBuffer.value
        if (frame.height <= 0 || frame.width <= 0) {
            frameBuffer.icon = null
            return
        }

        val image = UIUtil.createImage(this, frame.width, frame.height, BufferedImage.TYPE_INT_RGB)
        image.renderFrame(frame)
        frameBuffer.icon = ImageIcon(image) // TODO[F]: Find a clever way to update that (#40)
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
            errorView.value.text = errorMessage
    }
}
