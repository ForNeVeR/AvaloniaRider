package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.util.Alarm
import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings
import me.fornever.avaloniarider.previewer.AvaloniaMessageMouseListener
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.previewer.renderFrame
import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JComponent

class PreviewImageView(
    lifetime: Lifetime,
    private val controller: AvaloniaPreviewerSessionController,
    private val settings: AvaloniaSettings
) : JComponent() {
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

    private var lastFrame: FrameMessage? = null
    private var lastFrameSentNanoTime: Long? = null

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

        lastFrame?.let {
            doWithFpsLimit {
                controller.acknowledgeFrame(it)
                lastFrame = null
            }
        }
    }

    fun resetImage() {
        application.assertIsDispatchThread()
        buffer = null
        lastFrame = null
        lastFrameSentNanoTime = null
    }

    fun render(frame: FrameMessage) {
        application.assertIsDispatchThread()

        val image = if (buffer?.width == frame.width && buffer?.height == frame.height)
            buffer!!
        else {
            @Suppress("UndesirableClassUsage")
            BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB).also {
                revalidate()
            }
        }

        image.renderFrame(frame)

        buffer = image
        lastFrame = frame

        repaint()
    }

    private val alarm = Alarm(
        lifetime.createNestedDisposable("me.fornever.avaloniarider.idea.editor.PreviewImageView.lifetime"))
    private fun doWithFpsLimit(action: () -> Unit) {
        val fpsLimit = settings.fpsLimit
        val currentNanos = System.nanoTime()
        if (fpsLimit <= 0) {
            lastFrameSentNanoTime = currentNanos
            action()
            return
        }

        val nanosBetweenFrames = 1e9 / fpsLimit
        val lastSentNanos = lastFrameSentNanoTime
        if (lastSentNanos == null || currentNanos - lastSentNanos >= nanosBetweenFrames) {
            lastFrameSentNanoTime = currentNanos
            action()
            return
        }

        val nanosAlreadyWaited = currentNanos - lastSentNanos
        val nanosToWait = nanosBetweenFrames - nanosAlreadyWaited
        val millisToWait = (nanosToWait / 1e6).toLong()

        alarm.addRequest(action, millisToWait)
    }
}
