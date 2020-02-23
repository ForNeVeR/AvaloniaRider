package me.fornever.avaloniarider.previewer

import me.fornever.avaloniarider.controlmessages.FrameMessage
import java.awt.Color
import java.awt.image.BufferedImage

private fun fromByte(b: Byte): Int = b.toInt() and 0xFF

// TODO[F]: This is very suboptimal (#40)
private fun BufferedImage.fillWithColor(color: Color) {
    for (y in 0 until height) {
        for (x in 0 until width) {
            setRGB(x, y, color.rgb)
        }
    }
}

// TODO[F]: This is very suboptimal (#40)
fun BufferedImage.renderFrame(frame: FrameMessage) {
    this.fillWithColor(Color.WHITE)

    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            val pixelIndex = y * frame.width * 4 + x * 4 // 4 bytes per px
            val bytes = frame.data.slice(pixelIndex..pixelIndex + 3)
            val color = Color(fromByte(bytes[0]), fromByte(bytes[1]), fromByte(bytes[2]))
            setRGB(x, y, color.rgb)
        }
    }
}
