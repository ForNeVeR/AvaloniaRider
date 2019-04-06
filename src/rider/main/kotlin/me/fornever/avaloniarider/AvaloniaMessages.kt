package me.fornever.avaloniarider

import com.jetbrains.rider.util.idea.application
import com.jetbrains.rider.util.idea.getComponent
import java.util.*

private fun String.toUUID() = UUID.fromString(this)

class AvaloniaMessages {
    companion object {
        fun getInstance(): AvaloniaMessages = application.getComponent()
    }

    // TODO[F]: Fill these registries from annotations
    val incomingTypeRegistry: Map<UUID, Class<*>> =
            mapOf(
                    "854887cf-2694-4eb6-b499-7461b6fb96c7".toUUID() to StartDesignerSessionMessage::class.java,
                    "9b47b3d8-61df-4c38-acd4-8c1bb72554ac".toUUID() to RequestViewportResizeMessage::class.java,
                    "b7a70093-0c5d-47fd-9261-22086d43a2e2".toUUID() to UpdateXamlResultMessage::class.java,
                    "F58313EE-FE69-4536-819D-F52EDF201A0E".toUUID() to FrameMessage::class.java
            )
    val outgoingTypeRegistry: Map<Class<*>, UUID> =
            mapOf(
                    UpdateXamlMessage::class.java to "9aec9a2e-6315-4066-b4ba-e9a9efd0f8cc".toUUID(),
                    ClientSupportedPixelFormatsMessage::class.java to "63481025-7016-43FE-BADC-F2FD0F88609E".toUUID(),
                    ClientRenderInfoMessage::class.java to "7A3c25d3-3652-438D-8EF1-86E942CC96C0".toUUID(),
                    ClientViewportAllocatedMessage::class.java to "BD7A8DE6-3DB8-4A13-8583-D6D4AB189A31".toUUID(),
                    FrameReceivedMessage::class.java to "68014F8A-289D-4851-8D34-5367EDA7F827".toUUID()
            )
}

sealed class AvaloniaMessage

class StartDesignerSessionMessage : AvaloniaMessage() {
    val sessionId : String = ""
}

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

class UpdateXamlResultMessage : AvaloniaMessage() {
    val error: String? = ""
    val handle: String? = ""
}

class FrameMessage : AvaloniaMessage() {
    var sequenceId: Long = 0L
    var format: Int = 0
    var data: ByteArray = byteArrayOf()
    var width: Int = 0
    var height: Int = 0
    var stride: Int = 0
}

class RequestViewportResizeMessage : AvaloniaMessage() {
    val width: Double = 0.0
    val height: Double = 0.0
}

class ClientSupportedPixelFormatsMessage(
        val formats: IntArray
) : AvaloniaMessage()

class ClientRenderInfoMessage(
        val dpiX: Double,
        val dpiY: Double
) : AvaloniaMessage()

class ClientViewportAllocatedMessage(
        val width: Double,
        val height: Double,
        val dpiX: Double,
        val dpiY: Double
) : AvaloniaMessage()

class FrameReceivedMessage(
        val sequenceId: Long
) : AvaloniaMessage()
