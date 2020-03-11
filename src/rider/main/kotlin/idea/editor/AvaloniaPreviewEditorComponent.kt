package me.fornever.avaloniarider.idea.editor

import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.util.idea.application
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.concurrency.adviseOnUiThread
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.previewer.renderFrame
import java.awt.FlowLayout
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
            // TODO[F]: Update the image size for the renderer (#40)
        }

        controller.frame.adviseOnUiThread(lifetime) { frame ->
            drawFrame(frame)
            controller.acknowledgeFrame(frame)
        }

        // TODO[F]: Handle controller.status and controller.errorMessage (#41)
    }

    private fun drawFrame(frame: FrameMessage) {
        application.assertIsDispatchThread()
        if (frame.height <= 0 || frame.width <= 0) {
            content.icon = null
            return
        }

        val image = UIUtil.createImage(this, frame.width, frame.height, BufferedImage.TYPE_INT_RGB)
        image.renderFrame(frame)
        content.icon = ImageIcon(image) // TODO[F]: Find a clever way to update that (#40)
    }
}
