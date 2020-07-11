package me.fornever.avaloniarider.idea.editor

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.concurrency.adviseOnUiThread
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.previewer.nonTransparent
import me.fornever.avaloniarider.previewer.renderFrame
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel

class BitmapPreviewEditorComponent(lifetime: Lifetime, controller: AvaloniaPreviewerSessionController) : JPanel() {

    private val content = JLabel()
    init {
        layout = BorderLayout()
        add(JBScrollPane(content), BorderLayout.CENTER)

        controller.requestViewportResize.advise(lifetime) {
            // TODO[F]: Update the image size for the renderer (#40)
        }

        controller.frame.adviseOnUiThread(lifetime) { frame ->
            if (nonTransparent(frame)) // TODO[F]: Remove after fix of https://github.com/AvaloniaUI/Avalonia/issues/4264
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
