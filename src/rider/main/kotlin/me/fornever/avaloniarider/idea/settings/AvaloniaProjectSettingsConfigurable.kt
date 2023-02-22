package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class AvaloniaProjectSettingsConfigurable(private val project: Project) : Configurable {
    override fun getDisplayName() = "Avalonia"

    private var control: AvaloniaProjectSettingsComponent? = null

    override fun createComponent(): JComponent {
        val settings = AvaloniaProjectSettings.getInstance(project).state
        return AvaloniaProjectSettingsComponent(settings).apply {
            control = this
        }
    }

    override fun disposeUIResources() {
        control = null
    }

    override fun isModified(): Boolean {
        val modified = control?.isModified
        return modified ?: false
    }

    override fun apply() {
        val settings = control!!.currentState
        AvaloniaProjectSettings.getInstance(project).state.copyFrom(settings)
    }

    override fun reset() {
        control!!.currentState = AvaloniaProjectSettings.getInstance(project).state
    }
}
