package me.fornever.avaloniarider.previewer

import me.fornever.avaloniarider.controlmessages.FrameMessage
import java.awt.image.BufferedImage

fun nonTransparent(frame: FrameMessage): Boolean {
    return frame.data.any { it != 0.toByte() }
}

private fun fromByte(b: Byte): Int = b.toInt() and 0xFF

// TODO[F]: This is very suboptimal (#40)
var pixels = IntArray(0)
fun BufferedImage.renderFrame(frame: FrameMessage) {
//    val size = frame.data.size

    val buffer = raster.dataBuffer


//    if (pixels.size != size)
//        pixels = IntArray(size)

    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            val pixelIndex = y * frame.width * 4 + x * 4 // 4 bytes per px
//            val bytes = frame.data.slice(pixelIndex..pixelIndex + 3)
            // RBGA → ARGB
            val r = fromByte(frame.data[pixelIndex])
            val g = fromByte(frame.data[pixelIndex + 1])
            val b = fromByte(frame.data[pixelIndex + 2])
            val a = fromByte(frame.data[pixelIndex + 3])
            val color = a.shl(24) or r.shl(16) or g.shl(8) or b

//                    .or(fromByte(frame.data[pixelIndex + 1]).shl(16))
//                    .or(fromByte(frame.data[pixelIndex + 2]).shl(8))
//                    .or(fromByte(frame.data[pixelIndex + 3]))
            buffer.setElem(x + y * frame.width, color)
//            pixels[x + y * frame.width] = color
        }
    }

//    for (i in 0 until size)
//        pixels[i] = frame.data[i].toInt()

//    val buffer = raster.dataBuffer

//    raster.setPixels(0, 0, frame.width, frame.height, pixels)

//    setRGB(0, 0, frame.width, frame.height, pixels, 0, frame.width)
}
