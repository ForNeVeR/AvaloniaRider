package me.fornever.avaloniarider.idea.editor

import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.previewer.AvaloniaMessageMouseListener
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.previewer.renderFrame
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JComponent

class PreviewImageView(lifetime: Lifetime, controller: AvaloniaPreviewerSessionController) : JComponent() {
    init {
        val listener = AvaloniaMessageMouseListener(this)
        listener.avaloniaInputEvent.advise(lifetime) { message ->
            controller.sendInputEventMessage(message)
        }
        addMouseListener(listener)
        addMouseMotionListener(listener)
        addMouseWheelListener(listener)
    }

    var buffer: BufferedImage? = null

    val shiftImageX: Int
        get() = (width - (buffer?.width ?: 0)) / 2

    val shiftImageY: Int
        get() = (height - (buffer?.height ?: 0)) / 2

    override fun getPreferredSize(): Dimension {
        return buffer?.let { Dimension(it.width, it.height) } ?: super.getPreferredSize()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        g.color = UIUtil.getControlColor()
        g.fillRect(0, 0, width, height)
        buffer?.let { image ->
            g.drawImage(image, shiftImageX, shiftImageY, image.width, image.height, null)
        }
    }

    fun resetImage() {
        application.assertIsDispatchThread()
        buffer = null
    }

    fun render(frame: FrameMessage) {
        application.assertIsDispatchThread()

        buffer = UIUtil.createImage(this, frame.width, frame.height, BufferedImage.TYPE_INT_RGB).apply {
            renderFrame(frame) // TODO[F]: Find a clever way to update that (#40)
        }
        repaint()
    }
}
