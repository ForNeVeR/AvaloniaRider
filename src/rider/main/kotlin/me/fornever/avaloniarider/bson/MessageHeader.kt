package me.fornever.avaloniarider.bson

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

data class MessageHeader(val length: Int, val typeId: UUID) {
    companion object {
        fun fromBytes(bytes: ByteArray): MessageHeader {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
            val length = buffer.int

            fun readDotNetGuid(): UUID {
                val a = buffer.order(ByteOrder.LITTLE_ENDIAN).int
                val b = buffer.short
                val c = buffer.short
                val long1 = (ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).apply {
                    putInt(a)
                    putShort(b)
                    putShort(c)
                }.position(0) as ByteBuffer).long
                val long2 = buffer.order(ByteOrder.BIG_ENDIAN).long
                return UUID(long1, long2)
            }

            val messageId = readDotNetGuid()
            return MessageHeader(length, messageId)
        }
    }
}
