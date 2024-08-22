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

class AvaloniaProjectSettingsState : BaseState() {
    var previewerMethod by enum(AvaloniaPreviewerMethod.AvaloniaRemote)

    /**
     * Synchronize current run configuration and selected project, when possible.
     */
    var synchronizeWithRunConfiguration by property(false)

    var fpsLimit by property(0)

    var isDarkTheme by property(false)
}

@State(name = "Avalonia") // TODO[#265]: Move to avalonia.xml
@Service
class AvaloniaProjectSettings : SimplePersistentStateComponent<AvaloniaProjectSettingsState>(AvaloniaProjectSettingsState()) {
    companion object {
        fun getInstance(project: Project): AvaloniaProjectSettings = project.getService(AvaloniaProjectSettings::class.java)
    }

    val previewerTransportType: AvaloniaPreviewerMethod
        get() = state.previewerMethod

    val synchronizeWithRunConfiguration: Boolean
        get() = state.synchronizeWithRunConfiguration

    val fpsLimit: Int
        get() = state.fpsLimit

    val isDarkTheme: Boolean
        get() = state.isDarkTheme
}
