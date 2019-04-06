package me.fornever.avaloniarider

import com.fasterxml.jackson.annotation.JsonProperty
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
                    "b7a70093-0c5d-47fd-9261-22086d43a2e2".toUUID() to UpdateXamlResultMessage::class.java)
    val outgoingTypeRegistry: Map<Class<*>, UUID> =
            mapOf(UpdateXamlMessage::class.java to "9aec9a2e-6315-4066-b4ba-e9a9efd0f8cc".toUUID())
}

sealed class AvaloniaMessage

class StartDesignerSessionMessage : AvaloniaMessage() {

    @JsonProperty("SessionId")
    val sessionId : String = ""
}

class UpdateXamlMessage : AvaloniaMessage() {

    @JsonProperty("Xaml")
    var xaml: String = ""
    @JsonProperty("AssemblyPath")
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
    @JsonProperty("Error")
    val error: String = ""

    @JsonProperty("Handle")
    val handle: String = ""
}

class RequestViewportResizeMessage : AvaloniaMessage() {
    @JsonProperty("Width")
    val width: Double = 0.0

    @JsonProperty("Height")
    val height: Double = 0.0
}
