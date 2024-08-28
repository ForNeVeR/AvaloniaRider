package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.launch
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.settings.AvaloniaApplicationSettings

class DebugPreviewerAction(private val lifetime: Lifetime) : AnAction(
    AvaloniaRiderBundle.messagePointer("action.debug-previewer.text"),
    AvaloniaRiderBundle.messagePointer("action.debug-previewer.description"),
    AllIcons.Debugger.Console
) {
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
         lifetime.coroutineScope.launch {
             TODO("Get the debugger process startup command")
             TODO("Start the debug session")
         }
    }
}
