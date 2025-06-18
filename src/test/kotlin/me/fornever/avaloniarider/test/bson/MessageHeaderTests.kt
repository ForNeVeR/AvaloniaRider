package me.fornever.avaloniarider.test.bson

import com.jetbrains.rider.test.asserts.shouldBe
import me.fornever.avaloniarider.bson.MessageHeader
import org.testng.annotations.Test
import java.util.*

class MessageHeaderTests {
    val byteData = intArrayOf(0x39, 0x0, 0x0, 0x0,
        0xCF, 0x87, 0x48, 0x85, 0x94, 0x26, 0xB6, 0x4E,
        0xB4, 0x99, 0x74, 0x61, 0xB6, 0xFB, 0x96, 0xC7)
        .map { it.toByte() }
        .toByteArray()
    val guid = UUID.fromString("854887CF-2694-4EB6-B499-7461B6FB96C7")

    @Test
    fun testDeserialization() {
        val expected = MessageHeader(57, guid)
        MessageHeader.fromBytes(byteData).shouldBe(expected)
    }

    @Test
    fun testSerialization() {
        val header = MessageHeader(57, guid)
        val res = header.toByteArray()
        res.toTypedArray().shouldBe(byteData.toTypedArray())
    }
}
