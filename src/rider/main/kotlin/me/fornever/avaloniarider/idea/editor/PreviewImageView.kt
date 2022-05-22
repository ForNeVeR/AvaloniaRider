package me.fornever.avaloniarider.idea.editor

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.rd.paint2DLine
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings
import me.fornever.avaloniarider.previewer.AvaloniaMessageMouseListener
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.previewer.renderFrame
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.JComponent

class PreviewImageView(
    lifetime: Lifetime,
    private val controller: AvaloniaPreviewerSessionController,
    private val settings: AvaloniaSettings
) : JComponent() {

    companion object {
        private val backgroundColorLight = Color(0xFF, 0xFF, 0xFF)
        private val thickLineColorLight = Color(14.0f / 255.0f, 94.0f / 255.0f, 253.0f / 255.0f, 0.3f)
        private val lineColorLight = Color(14.0f / 255.0f, 94.0f / 255.0f, 253.0f / 255.0f, 0.1f)

        private val backgroundColorDark = Color(24, 24, 24)
        private val thickLineColorDark = Color(11.0f / 255.0f, 94.0f / 255.0f, 253.0f / 255.0f, 0.3f)
        private val lineColorDark = Color(11.0f / 255.0f, 94.0f / 255.0f, 253.0f / 255.0f, 0.1f)
    }

    init {
        val listener = AvaloniaMessageMouseListener(this)
        listener.avaloniaInputEvent.advise(lifetime) { message ->
            controller.sendInputEventMessage(message)
        }
        listener.zoomEvent.advise(lifetime) { zoomFactor ->
            controller.zoomFactor.value = zoomFactor
        }
        addMouseListener(listener)
        addMouseMotionListener(listener)
        addMouseWheelListener(listener)
    }

    var buffer: BufferedImage? = null

    private var lastFrame: FrameMessage? = null
    private var lastFrameSentNanoTime: Long? = null

    private val screenScale: Float
        get() = JBUIScale.sysScale(this)

    private val scaledBufferSize: Dimension?
        get() = buffer?.let {
            val scale = screenScale
            Dimension((it.width / scale).toInt(), (it.height / scale).toInt())
        }

    val shiftImageX: Int
        get() = (width - (scaledBufferSize?.width ?: 0)) / 2

    val shiftImageY: Int
        get() = (height - (scaledBufferSize?.height ?: 0)) / 2

    override fun getPreferredSize(): Dimension {
        return scaledBufferSize ?: super.getPreferredSize()
    }

    private fun isDarkTheme(): Boolean {
        return if (UIUtil.isUnderDarcula()) {
            true
        } else {
            val currentLafInfo = LafManager.getInstance().currentLookAndFeel
            val theme = (currentLafInfo as? UIThemeBasedLookAndFeelInfo)?.theme
            theme?.isDark ?: false
        }
    }

    private fun paintGrid(g: Graphics2D) {
        val (thickLineColor, lineColor) =
            if (isDarkTheme())
                thickLineColorDark to lineColorDark
            else
                thickLineColorLight to lineColorLight

        for (i in 1..(width / 10)) {
            var color = lineColor

            if (i % 10 == 0) {
                color = thickLineColor
            }

            g.paint2DLine(
                i * 10.0,
                0.0,
                i * 10.0,
                height.toDouble(),
                strokeType = LinePainter2D.StrokeType.CENTERED,
                strokeWidth = 1.0,
                color
            )
        }

        for (i in 1..(height / 10)) {
            var color = lineColor

            if (i % 10 == 0) {
                color = thickLineColor
            }

            g.paint2DLine(
                0.0,
                i * 10.0,
                width.toDouble(),
                i * 10.0,
                strokeType = LinePainter2D.StrokeType.CENTERED,
                strokeWidth = 1.0,
                color
            )
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        g.color = if (isDarkTheme()) backgroundColorDark else backgroundColorLight
        g.fillRect(0, 0, width, height)

        paintGrid(g as Graphics2D)

        buffer?.let { image ->
            val scaledSize = scaledBufferSize!!
            g.drawImage(image, shiftImageX, shiftImageY, scaledSize.width, scaledSize.height, null)
        }

        lastFrame?.let {
            doWithFpsLimit {
                controller.acknowledgeFrame(it)
                lastFrame = null
            }
        }

        controller.dpi.set(96.0 * screenScale)
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

    private val fpsTimer = Alarm(
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

        fpsTimer.addRequest(action, millisToWait)
    }
}
