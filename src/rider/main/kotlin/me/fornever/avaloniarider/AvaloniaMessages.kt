package me.fornever.avaloniarider

import com.jetbrains.rider.util.idea.application
import com.jetbrains.rider.util.idea.getComponent
import java.util.*

class AvaloniaMessages {
    companion object {
        fun getInstance(): AvaloniaMessages = application.getComponent()
    }
    val typeRegistry: Map<UUID, Class<*>> = emptyMap()
}
