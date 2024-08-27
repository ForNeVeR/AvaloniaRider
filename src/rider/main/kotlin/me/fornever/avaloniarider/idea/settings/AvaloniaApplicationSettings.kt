package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "AvaloniaRider", storages = [Storage("AvaloniaRider.xml")])
@Service
class AvaloniaApplicationSettings : SimplePersistentStateComponent<AvaloniaApplicationState>(
    AvaloniaApplicationState()
) {
    companion object {
        fun getInstance(): AvaloniaApplicationSettings = service()
    }

    val isDeveloperModeEnabled: Boolean
        get() = state.isDeveloperModeEnabled
}

class AvaloniaApplicationState : BaseState() {
    var isDeveloperModeEnabled by property(false)
}
