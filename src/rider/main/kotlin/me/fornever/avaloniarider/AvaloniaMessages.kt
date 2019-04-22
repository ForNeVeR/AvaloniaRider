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
                        incomingTypeRegistry.put(annotation.uuid.toUUID(), type::class.java)
                    is AvaloniaOutgoingMessage ->
                        outgoingTypeRegistry.put(type::class.java, annotation.uuid.toUUID())
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
class UpdateXamlMessage : AvaloniaMessage() {
    var xaml: String = ""
    var assemblyPath: String = ""
}

class UpdateXamlMessageBuilder {
    companion object {
        fun build(xaml: String, assemblyPath: String) =
                UpdateXamlMessage().apply {
                    this.xaml = xaml
                    this.assemblyPath = assemblyPath
                }
    }
}

@AvaloniaIncomingMessage("b7a70093-0c5d-47fd-9261-22086d43a2e2")
class UpdateXamlResultMessage : AvaloniaMessage() {
    val error: String? = ""
    val handle: String? = ""
}

@AvaloniaIncomingMessage("F58313EE-FE69-4536-819D-F52EDF201A0E")
class FrameMessage : AvaloniaMessage() {
    var sequenceId: Long = 0L
    var format: Int = 0
    var data: ByteArray = byteArrayOf()
    var width: Int = 0
    var height: Int = 0
    var stride: Int = 0
}

@AvaloniaIncomingMessage("9b47b3d8-61df-4c38-acd4-8c1bb72554ac")
class RequestViewportResizeMessage : AvaloniaMessage() {
    val width: Double = 0.0
    val height: Double = 0.0
}

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
