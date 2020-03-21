package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project

enum class AvaloniaPreviewerMethod {
    AvaloniaRemote,
    Html
}

class AvaloniaSettingsState : BaseState() {
    var previewerMethod by enum(AvaloniaPreviewerMethod.AvaloniaRemote)
}

@State(name = "Avalonia")
@Service
class AvaloniaSettings : SimplePersistentStateComponent<AvaloniaSettingsState>(AvaloniaSettingsState()) {
    companion object {
        fun getInstance(project: Project): AvaloniaSettings = project.getService(AvaloniaSettings::class.java)
    }

    val previewerTransportType: AvaloniaPreviewerMethod
        get() = state.previewerMethod
}
