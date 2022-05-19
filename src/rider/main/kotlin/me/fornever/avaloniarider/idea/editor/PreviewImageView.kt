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
    init {
        val listener = AvaloniaMessageMouseListener(this)
        listener.avaloniaInputEvent.advise(lifetime) { message ->
            controller.sendInputEventMessage(message)
        }
        listener.zoomEvent.advise(lifetime) { zoomFactor ->
            controller.setZoomFactor(zoomFactor);
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

    private fun isDark (): Boolean {
        if(UIUtil.isUnderDarcula())
        {
            return true;
        }
        else {
            val lafManager = LafManager.getInstance()
            val currentLafInfo = lafManager.currentLookAndFeel
            val theme = if (currentLafInfo is UIThemeBasedLookAndFeelInfo) currentLafInfo.theme else null
            return theme != null && theme.isDark
        }
    }

    private fun paintGrid(g: Graphics)
    {
        var context = g as Graphics2D;

        var thickLineColor = Color(14.0f / 255.0f, 94.0f / 255.0f, 253.0f / 255.0f, 0.3f);
        var lineColor = Color(14.0f / 255.0f, 94.0f / 255.0f, 253.0f / 255.0f, 0.1f);

        if(isDark())
        {
            thickLineColor = Color(11.0f / 255.0f, 94.0f / 255.0f, 253.0f / 255.0f, 0.3f);
            lineColor = Color(11.0f / 255.0f, 94.0f / 255.0f, 253.0f / 255.0f, 0.1f);
        }

        var height = height.toDouble();
        var width = width.toDouble();

        for(i in 1 .. (this.width / 10))
        {
            var color = lineColor;

            if(i % 10 == 0)
            {
                color = thickLineColor;
            }

            context.paint2DLine(i * 10.0, 0.0, i * 10.0, height, strokeType = LinePainter2D.StrokeType.CENTERED, strokeWidth = 1.0, color);
        }

        for(i in 1 .. (this.height / 10))
        {
            var color = lineColor;

            if(i % 10 == 0)
            {
                color = thickLineColor;
            }

            context.paint2DLine(0.0, i * 10.0, width, i * 10.0, strokeType = LinePainter2D.StrokeType.CENTERED, strokeWidth = 1.0, color);
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        var sysscale = JBUIScale.sysScale();

        if(isDark())
        {
            g.color = Color(24,24,24);
        }
        else
        {
            g.color = Color(0xFF, 0xFF, 0xFF);
        }

        g.fillRect(0, 0, width, height)

        paintGrid(g);

        buffer?.let { image ->
            g.drawImage(image, shiftImageX, shiftImageY, (image.width / sysscale).toInt(), (image.height / sysscale).toInt(), null)
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
