package me.fornever.avaloniarider

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.jetbrains.rider.util.idea.application
import com.jetbrains.rider.util.idea.getComponent
import me.fornever.avaloniarider.bson.MessageHeader
import java.util.*

private fun String.toUUID() = UUID.fromString(this)

class AvaloniaMessages {
    companion object {
        fun getInstance(): AvaloniaMessages = application.getComponent()
    }
    val typeRegistry: Map<UUID, Class<*>> =
            listOf(StartDesignerSessionMessage()).map {
                mesType -> mesType.guid to mesType.javaClass
            }.toMap()
}

abstract class AvaloniaMessage(val guid : UUID)

class StartDesignerSessionMessage
    : AvaloniaMessage("854887cf-2694-4eb6-b499-7461b6fb96c7".toUUID()) {

    @JsonProperty("SessionId")
    val sessionId : String = ""
}

class UpdateXamlMessage
    : AvaloniaMessage("9aec9a2e-6315-4066-b4ba-e9a9efd0f8cc".toUUID()) {

    @JsonProperty("Xaml")
    var xaml: String = ""
    @JsonProperty("AssemblyPath")
    var assemblyPath: String = ""
    @JsonProperty("XamlFileProjectPath")
    var xamlFileProjectPath: String = ""
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
