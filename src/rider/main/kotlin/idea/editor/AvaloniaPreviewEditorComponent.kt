package me.fornever.avaloniarider.idea.editor

import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.util.idea.application
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.previewer.*
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel

class AvaloniaPreviewEditorComponent(lifetime: Lifetime, controller: AvaloniaPreviewerSessionController) : JPanel() {

    private val content = JLabel()
    init {
        layout = FlowLayout()
        add(content)

        controller.requestViewportResize.advise(lifetime) {
            // TODO: Calculate UI scale
        }

        controller.frameReceived.advise(lifetime) { frame ->
            drawFrame(frame)
            controller.acknowledgeFrame(frame)
        }

        // TODO[F]: Handle controller.status and controller.errorMessage
    }

    fun drawFrame(frame: FrameMessage) {
        application.assertIsDispatchThread()

        val image = UIUtil.createImage(this, 250, 100, BufferedImage.TYPE_INT_RGB)
        image.renderFrame(frame)
        content.icon = ImageIcon(image) // TODO[F]: Find a clever way to update that
    }
}
