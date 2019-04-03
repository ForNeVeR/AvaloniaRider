package me.fornever.avaloniarider

import com.fasterxml.jackson.annotation.JsonProperty
import com.jetbrains.rider.util.idea.application
import com.jetbrains.rider.util.idea.getComponent
import java.util.*

class AvaloniaMessages {
    companion object {
        fun getInstance(): AvaloniaMessages = application.getComponent()
    }
    val typeRegistry: Map<UUID, Class<*>> =
            mapOf(
                    "854887cf-2694-4eb6-b499-7461b6fb96c7" to StartDesignerSessionMessage().javaClass
            ).map { (guid, mes) -> UUID.fromString(guid) to mes }.toMap()
}

class StartDesignerSessionMessage {
    @JsonProperty("SessionId")
    val sessionId : String = ""
}

