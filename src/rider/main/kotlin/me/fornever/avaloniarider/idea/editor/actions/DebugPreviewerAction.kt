package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import me.fornever.avaloniarider.idea.settings.AvaloniaApplicationSettings

class DebugPreviewerAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        if (!AvaloniaApplicationSettings.getInstance().isDeveloperModeEnabled) {
            presentation.isEnabledAndVisible = false
            return
        }

        presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(p0: AnActionEvent) {

    }
}
