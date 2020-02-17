package me.fornever.avaloniarider.idea.editor

import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.util.idea.application
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.previewer.*
import java.awt.Color
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.lang.Double.max
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max

class AvaloniaPreviewEditorComponent(lifetime: Lifetime, session: AvaloniaPreviewerSession) : JPanel() {

    private val content = JLabel()

    private lateinit var image: BufferedImage
    private fun initializeImage() {
        application.assertIsDispatchThread()

        // TODO[F]: Properly calculate width and height, or delay the initialization if they're zero
        image = UIUtil.createImage(this, 250, 100, BufferedImage.TYPE_INT_RGB)
    }

    init {
        layout = FlowLayout()
        add(content)

        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                initializeImage()
            }

            override fun componentResized(e: ComponentEvent?) {
                if (isVisible)
                    initializeImage()
            }
        })
        initializeImage()

        session.requestViewportSize.advise(lifetime) {
            // TODO: Calculate UI scale
        }

        session.frame.advise(lifetime) { frame ->
            drawFrame(frame)
        }
    }

    fun drawFrame(frame: FrameMessage) {
        application.assertIsDispatchThread()

        image = UIUtil.createImage(this, 250, 100, BufferedImage.TYPE_INT_RGB)
        image.renderFrame(frame)
        content.icon = ImageIcon(image) // TODO[F]: Find a clever way to update that
    }
}
