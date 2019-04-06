package me.fornever.avaloniarider.me.fornever.avaloniarider

import me.fornever.avaloniarider.FrameMessage
import java.awt.Color
import java.awt.FlowLayout
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

class AvaloniaPreviewerWindow : JFrame("Avalonia Previewer") {

    companion object {
        private fun fromByte(b: Byte): Int = b.toInt() and 0xFF
    }

    private val content = JLabel()
    init {
        layout = FlowLayout()
        contentPane.add(content)
    }

    fun drawFrame(frame: FrameMessage) {
        @Suppress("UndesirableClassUsage")
        val img = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB)

        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val pixelIndex = y * frame.width * 4 + x * 4 // 4 bytes per px
                val bytes = frame.data.slice(pixelIndex..pixelIndex + 3)
                val color = Color(fromByte(bytes[0]), fromByte(bytes[1]), fromByte(bytes[2]))
                img.setRGB(x, y, color.rgb)
            }
        }

        content.icon = ImageIcon(img)
    }
}
