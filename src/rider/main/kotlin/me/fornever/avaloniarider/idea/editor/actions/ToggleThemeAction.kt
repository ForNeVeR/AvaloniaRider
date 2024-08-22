package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import me.fornever.avaloniarider.idea.settings.AvaloniaProjectSettings

class ToggleThemeAction(private val projectSettings: AvaloniaProjectSettings) : ToggleAction() {

    override fun isSelected(e: AnActionEvent): Boolean {
        return projectSettings.state.isDarkTheme
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        projectSettings.state.isDarkTheme = state
    }
}
