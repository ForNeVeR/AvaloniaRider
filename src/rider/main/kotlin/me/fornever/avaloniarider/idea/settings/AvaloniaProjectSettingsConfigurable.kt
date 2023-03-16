package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class AvaloniaProjectSettingsConfigurable(private val project: Project) : Configurable {

    private val projectSettings = lazy { AvaloniaProjectSettings.getInstance(project) }
    private val workspaceSettings = lazy { AvaloniaWorkspaceSettings.getInstance(project) }
    private val globalControlState: AvaloniaProjectSettingsControlState
        get() = AvaloniaProjectSettingsControlState(projectSettings.value.state, workspaceSettings.value.state)

    override fun getDisplayName() = "Avalonia"

    private var component = lazy { AvaloniaProjectSettingsComponent() }

    override fun createComponent(): JComponent {
        return component.value.panel
    }

    override fun isModified() =
        component.let {
            it.currentState == globalControlState
        }

    override fun apply() {
        component.let {
            it.currentState.applyTo(projectSettings.value, workspaceSettings.value)
        }
    }

    override fun reset() {
        component.let {
            it.currentState = globalControlState
        }
    }
}
