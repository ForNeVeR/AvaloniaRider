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

class DefaultThemeProp {
    companion object {
        var themeApplicableTags = "Window,UserControl"
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

    /**
     * Use a shadow copy of the output assemblies to run the previewer.
     * This prevents the previewer from locking the output files, allowing the build to proceed.
     */
    var useShadowCopy by property(false)

    var fpsLimit by property(0)

    /**
     * The theme will apply only when these root tags are found in the file.
     */
    var themeApplicableTags by string(DefaultThemeProp.themeApplicableTags)

    var defaultTheme by enum(AvaloniaPreviewerTheme.None)

    var showThemeSelector by property(true)

    var darkThemeStyle by string(DefaultThemeProp.darkTheme)

    var lightThemeStyle by string(DefaultThemeProp.lightTheme)
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

    val useShadowCopy: Boolean
        get() = state.useShadowCopy

    val fpsLimit: Int
        get() = state.fpsLimit

    val themeApplicableTags: String
        get() = state.themeApplicableTags ?: DefaultThemeProp.themeApplicableTags

    val defaultTheme: AvaloniaPreviewerTheme
        get() = state.defaultTheme

    val showThemeSelector: Boolean
        get() = state.showThemeSelector

    val darkThemeStyle: String
        get() = state.darkThemeStyle ?: DefaultThemeProp.darkTheme

    val lightThemeStyle: String
        get() = state.lightThemeStyle ?: DefaultThemeProp.lightTheme
}
