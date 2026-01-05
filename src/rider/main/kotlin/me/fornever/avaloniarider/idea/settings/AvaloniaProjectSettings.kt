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

enum class AvaloniaPreviewerTheme {
    None,
    Light,
    Dark
}

class DefaultTheme {
    companion object {
        var darkTheme = """<Design.DesignStyle>
    <Style Selector="Window">
        <Setter Property="RequestedThemeVariant" Value="Dark" />
    </Style>
</Design.DesignStyle>"""
        var lightTheme = """<Design.DesignStyle>
    <Style Selector="Window">
        <Setter Property="RequestedThemeVariant" Value="Light" />
    </Style>
</Design.DesignStyle>"""
    }
}

class AvaloniaProjectSettingsState : BaseState() {
    var previewerMethod by enum(AvaloniaPreviewerMethod.AvaloniaRemote)

    /**
     * Synchronize current run configuration and selected project, when possible.
     */
    var synchronizeWithRunConfiguration by property(false)

    var fpsLimit by property(0)

    var defaultTheme by enum(AvaloniaPreviewerTheme.None)

    var darkThemeStyle by string(DefaultTheme.darkTheme)

    var lightThemeStyle by string(DefaultTheme.lightTheme)
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

    val defaultTheme: AvaloniaPreviewerTheme
        get() = state.defaultTheme

    val darkThemeStyle: String
        get() = state.darkThemeStyle ?: DefaultTheme.darkTheme

    val lightThemeStyle: String
        get() = state.lightThemeStyle ?: DefaultTheme.lightTheme
}
