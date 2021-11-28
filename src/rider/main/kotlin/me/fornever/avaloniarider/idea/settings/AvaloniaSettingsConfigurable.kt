package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class AvaloniaSettingsConfigurable(private val project: Project) : Configurable {
    override fun getDisplayName() = "Avalonia"

    private var control: AvaloniaSettingsComponent? = null

    override fun createComponent(): JComponent {
        val settings = AvaloniaSettings.getInstance(project).state
        return AvaloniaSettingsComponent(settings).apply {
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
        AvaloniaSettings.getInstance(project).state.copyFrom(settings)
    }

    override fun reset() {
        control!!.currentState = AvaloniaSettings.getInstance(project).state
    }
}
