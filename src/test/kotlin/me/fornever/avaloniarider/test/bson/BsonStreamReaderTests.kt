package me.fornever.avaloniarider.test.bson

import com.jetbrains.rider.test.asserts.shouldBe
import de.undercouch.bson4jackson.BsonFactory
import me.fornever.avaloniarider.bson.BsonStreamReader
import me.fornever.avaloniarider.bson.MessageHeader
import me.fornever.avaloniarider.controlmessages.AvaloniaMessages
import me.fornever.avaloniarider.controlmessages.FrameMessage
import me.fornever.avaloniarider.controlmessages.UpdateXamlResultMessage
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class BsonStreamReaderTests {

    private val typeRegistry = AvaloniaMessages().incomingTypeRegistry

    private fun read(messageType: Class<*>, body: Map<String, Any?>): Any? {
        val bytes = ObjectMapper(BsonFactory()).writeValueAsBytes(body)
        val typeId = typeRegistry.entries.single { it.value == messageType }.key
        val stream = MessageHeader(bytes.size, typeId).toByteArray() + bytes
        return BsonStreamReader(typeRegistry, DataInputStream(ByteArrayInputStream(stream))).readMessage()
    }

    @Test
    fun successfulUpdateXamlResultHasNoError() {
        val message = read(
            UpdateXamlResultMessage::class.java,
            mapOf("Error" to null, "Handle" to "handle", "Exception" to null)
        ) as UpdateXamlResultMessage

        message.error.shouldBe(null)
        message.handle.shouldBe("handle")
    }

    @Test
    fun frameMessageIsFilled() {
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val message = read(
            FrameMessage::class.java,
            mapOf("SequenceId" to 42L, "Format" to 1, "Data" to data, "Width" to 2, "Height" to 1, "Stride" to 8)
        ) as FrameMessage

        message.sequenceId.shouldBe(42L)
        message.width.shouldBe(2)
        message.height.shouldBe(1)
        message.stride.shouldBe(8)
        message.data.toList().shouldBe(data.toList())
    }
}
