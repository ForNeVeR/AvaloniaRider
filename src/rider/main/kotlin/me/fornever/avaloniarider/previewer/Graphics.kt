package me.fornever.avaloniarider.previewer

import me.fornever.avaloniarider.controlmessages.FrameMessage
import java.awt.image.BufferedImage

fun nonTransparent(frame: FrameMessage): Boolean {
    return frame.data.any { it != 0.toByte() }
}

private fun fromByte(b: Byte): Int = b.toInt() and 0xFF

fun BufferedImage.renderFrame(frame: FrameMessage) {
    val buffer = raster.dataBuffer

    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            val pixelIndex = y * frame.width * 4 + x * 4 // 4 bytes per px

            // RBGA → ARGB
            val r = fromByte(frame.data[pixelIndex])
            val g = fromByte(frame.data[pixelIndex + 1])
            val b = fromByte(frame.data[pixelIndex + 2])
            val a = fromByte(frame.data[pixelIndex + 3])
            val color = a.shl(24) or r.shl(16) or g.shl(8) or b

            buffer.setElem(x + y * frame.width, color)
        }
    }
}
