package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class AvaloniaProjectSettingsConfigurable(private val project: Project) : Configurable {

    private val projectSettings by lazy { AvaloniaProjectSettings.getInstance(project) }
    private val workspaceSettings by lazy { AvaloniaWorkspaceSettings.getInstance(project) }
    private val globalControlState: AvaloniaProjectSettingsControlState
        get() = AvaloniaProjectSettingsControlState(projectSettings.state, workspaceSettings.state)

    override fun getDisplayName() = "Avalonia"

    private val component by lazy { AvaloniaProjectSettingsComponent(globalControlState) }

    override fun createComponent(): JComponent {
        return component.panel
    }

    override fun isModified() =
        component.currentState.get() == globalControlState

    override fun apply() {
        val state = component.currentState.get()
        projectSettings.state.apply(state)
        workspaceSettings.state.apply(state)
    }

    override fun reset() {
        component.currentState.set(globalControlState)
    }
}
