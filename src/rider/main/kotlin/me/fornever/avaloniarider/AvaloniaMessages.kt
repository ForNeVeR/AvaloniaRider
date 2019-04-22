package me.fornever.avaloniarider

import com.jetbrains.rider.util.idea.application
import com.jetbrains.rider.util.idea.getComponent
import me.fornever.avaloniarider.me.fornever.avaloniarider.AvaloniaIncomingMessage
import me.fornever.avaloniarider.me.fornever.avaloniarider.AvaloniaOutgoingMessage
import java.util.*

class AvaloniaMessages {
    companion object {
        fun getInstance(): AvaloniaMessages = application.getComponent()
    }

    val incomingTypeRegistry = mutableMapOf<UUID, Class<*>>()
    val outgoingTypeRegistry = mutableMapOf<Class<*>, UUID>()

    init {
        val declaredMessageTypes = AvaloniaMessage::class.nestedClasses
        //val declaredMessageTypes = AvaloniaMessage::class.sealedSubclasses
        for (type in declaredMessageTypes) {
            for (annotation in type.annotations) {
                when (annotation) {
                    is AvaloniaIncomingMessage ->
                        incomingTypeRegistry.put(annotation.uuid.toUUID(), type.java)
                    is AvaloniaOutgoingMessage ->
                        outgoingTypeRegistry.put(type.java, annotation.uuid.toUUID())
                }
            }
        }
    }
}

sealed class AvaloniaMessage

@AvaloniaIncomingMessage("854887cf-2694-4eb6-b499-7461b6fb96c7")
class StartDesignerSessionMessage : AvaloniaMessage() {
    val sessionId : String = ""
}

@AvaloniaOutgoingMessage("9aec9a2e-6315-4066-b4ba-e9a9efd0f8cc")
class UpdateXamlMessage(
        val xaml: String = "",
        val assemblyPath: String = ""
) : AvaloniaMessage()

@AvaloniaIncomingMessage("b7a70093-0c5d-47fd-9261-22086d43a2e2")
class UpdateXamlResultMessage(
        val error: String? = "",
        val handle: String? = ""
) : AvaloniaMessage()

@AvaloniaIncomingMessage("F58313EE-FE69-4536-819D-F52EDF201A0E")
class FrameMessage(
        val sequenceId: Long = 0L,
        val format: Int = 0,
        val data: ByteArray = byteArrayOf(),
        val width: Int = 0,
        val height: Int = 0,
        val stride: Int = 0
) : AvaloniaMessage()

@AvaloniaIncomingMessage("9b47b3d8-61df-4c38-acd4-8c1bb72554ac")
class RequestViewportResizeMessage(
        val width: Double = 0.0,
        val height: Double = 0.0
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("63481025-7016-43FE-BADC-F2FD0F88609E")
class ClientSupportedPixelFormatsMessage(
        val formats: IntArray
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("7A3c25d3-3652-438D-8EF1-86E942CC96C0")
class ClientRenderInfoMessage(
        val dpiX: Double,
        val dpiY: Double
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("BD7A8DE6-3DB8-4A13-8583-D6D4AB189A31")
class ClientViewportAllocatedMessage(
        val width: Double,
        val height: Double,
        val dpiX: Double,
        val dpiY: Double
) : AvaloniaMessage()

@AvaloniaOutgoingMessage("68014F8A-289D-4851-8D34-5367EDA7F827")
class FrameReceivedMessage(
        val sequenceId: Long
) : AvaloniaMessage()
