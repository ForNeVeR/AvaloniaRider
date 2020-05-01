package me.fornever.avaloniarider.controlmessages

import com.jetbrains.rd.platform.util.application
import com.jetbrains.rider.util.idea.getComponent
import me.fornever.avaloniarider.toUUID
import java.util.*

class AvaloniaMessages {
    companion object {
        fun getInstance(): AvaloniaMessages = application.getComponent()
    }

    val incomingTypeRegistry = mutableMapOf<UUID, Class<*>>()
    val outgoingTypeRegistry = mutableMapOf<Class<*>, UUID>()

    init {
        val declaredMessageTypes = AvaloniaMessage::class.sealedSubclasses
        for (type in declaredMessageTypes) {
            for (annotation in type.annotations) {
                when (annotation) {
                    is AvaloniaIncomingMessage ->
                        incomingTypeRegistry[annotation.uuid.toUUID()] = type.java
                    is AvaloniaOutgoingMessage ->
                        outgoingTypeRegistry[type.java] = annotation.uuid.toUUID()
                }
            }
        }
    }
}

sealed class AvaloniaMessage

@AvaloniaIncomingMessage("854887cf-2694-4eb6-b499-7461b6fb96c7")
data class StartDesignerSessionMessage(
    val sessionId: String = ""
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("9aec9a2e-6315-4066-b4ba-e9a9efd0f8cc")
data class UpdateXamlMessage(
    val xaml: String = "",
    val assemblyPath: String = "",
    val xamlFileProjectPath: String = ""
) : AvaloniaMessage()

data class ExceptionDetails(
    val exceptionType: String? = "",
    val message: String? = "",
    val lineNumber: Int? = null,
    val linePosition: Int? = null
)

@AvaloniaIncomingMessage("b7a70093-0c5d-47fd-9261-22086d43a2e2")
data class UpdateXamlResultMessage(
    val error: String? = "",
    val handle: String? = "",
    val exception: ExceptionDetails? = null
) : AvaloniaMessage()

@AvaloniaIncomingMessage("F58313EE-FE69-4536-819D-F52EDF201A0E")
data class FrameMessage(
    val sequenceId: Long = 0L,
    val format: Int = 0,
    val data: ByteArray = byteArrayOf(),
    val width: Int = 0,
    val height: Int = 0,
    val stride: Int = 0
) : AvaloniaMessage()

@AvaloniaIncomingMessage("9b47b3d8-61df-4c38-acd4-8c1bb72554ac")
data class RequestViewportResizeMessage(
    val width: Double = 0.0,
    val height: Double = 0.0
) : AvaloniaMessage()

@AvaloniaIncomingMessage("53778004-78fa-4381-8ec3-176a6f2328b6")
data class HtmlTransportStartedMessage(
    val uri: String = ""
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("63481025-7016-43FE-BADC-F2FD0F88609E")
data class ClientSupportedPixelFormatsMessage(
    val formats: IntArray
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("7A3c25d3-3652-438D-8EF1-86E942CC96C0")
data class ClientRenderInfoMessage(
    val dpiX: Double,
    val dpiY: Double
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("BD7A8DE6-3DB8-4A13-8583-D6D4AB189A31")
data class ClientViewportAllocatedMessage(
    val width: Double,
    val height: Double,
    val dpiX: Double,
    val dpiY: Double
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("68014F8A-289D-4851-8D34-5367EDA7F827")
data class FrameReceivedMessage(
    val sequenceId: Long
) : AvaloniaMessage()
