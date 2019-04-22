package me.fornever.avaloniarider.me.fornever.avaloniarider

import me.fornever.avaloniarider.FrameMessage
import java.awt.Color
import java.awt.FlowLayout
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

const val TOOL_WINDOW_WIDTH  = 250
const val TOOL_WINDOW_HEIGHT = 70

class AvaloniaPreviewerWindow : JFrame("Avalonia Previewer") {

    companion object {
        @Suppress("UndesirableClassUsage")
        val img = BufferedImage(TOOL_WINDOW_WIDTH, TOOL_WINDOW_HEIGHT, BufferedImage.TYPE_INT_RGB)

        private fun fromByte(b: Byte): Int = b.toInt() and 0xFF

        private fun fillImage(img: BufferedImage, color: Color) {
            for (y in 0 until img.height) {
                for (x in 0 until img.width) {
                    img.setRGB(x, y, color.rgb)
                }
            }
        }
    }

    private val content = JLabel()
    init {
        layout = FlowLayout()
        contentPane.add(content)
    }

    fun drawFrame(frame: FrameMessage) {
        fillImage(img, Color.WHITE)

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
