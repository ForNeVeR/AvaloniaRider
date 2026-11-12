package me.fornever.avaloniarider.test.bson

import com.jetbrains.rider.test.asserts.shouldBe
import de.undercouch.bson4jackson.BsonFactory
import me.fornever.avaloniarider.bson.BsonStreamReader
import me.fornever.avaloniarider.bson.MessageHeader
import me.fornever.avaloniarider.controlmessages.*
import me.fornever.avaloniarider.toUUID
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class BsonStreamReaderTests {

    private val bsonWriter = ObjectMapper(BsonFactory())

    private inline fun <reified T> readMessage(body: Map<String, Any?>): Any? {
        val typeId = T::class.java.getAnnotation(AvaloniaIncomingMessage::class.java).uuid.toUUID()
        val bodyBytes = bsonWriter.writeValueAsBytes(body)
        val headerBytes = MessageHeader(bodyBytes.size, typeId).toByteArray()
        val stream = DataInputStream(ByteArrayInputStream(headerBytes + bodyBytes))
        return BsonStreamReader(AvaloniaMessages().incomingTypeRegistry, stream).readMessage()
    }

    @Test
    fun frameMessageIsDeserialized() {
        val message = readMessage<FrameMessage>(linkedMapOf(
            "SequenceId" to 42L,
            "Format" to 1,
            "Data" to byteArrayOf(1, 2, 3),
            "Width" to 640,
            "Height" to 480,
            "Stride" to 2560
        )) as FrameMessage

        message.sequenceId.shouldBe(42L)
        message.format.shouldBe(1)
        message.data.contentEquals(byteArrayOf(1, 2, 3)).shouldBe(true)
        message.width.shouldBe(640)
        message.height.shouldBe(480)
        message.stride.shouldBe(2560)
    }

    @Test
    fun updateXamlResultMessageWithNestedExceptionIsDeserialized() {
        val message = readMessage<UpdateXamlResultMessage>(linkedMapOf(
            "Error" to "Unexpected token",
            "Handle" to null,
            "Exception" to linkedMapOf(
                "ExceptionType" to "XamlParseException",
                "Message" to "Unexpected token",
                "LineNumber" to 3,
                "LinePosition" to 14
            )
        ))

        message.shouldBe(UpdateXamlResultMessage(
            error = "Unexpected token",
            handle = null,
            exception = ExceptionDetails(
                exceptionType = "XamlParseException",
                message = "Unexpected token",
                lineNumber = 3,
                linePosition = 14
            )
        ))
    }
}
