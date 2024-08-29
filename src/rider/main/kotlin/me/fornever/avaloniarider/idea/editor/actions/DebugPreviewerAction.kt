package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.hasValue
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.settings.AvaloniaApplicationSettings
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import me.fornever.avaloniarider.previewer.ProcessExecutionMode
import java.nio.file.Path

class DebugPreviewerAction(
    private val lifetime: Lifetime,
    private val sessionController: AvaloniaPreviewerSessionController,
    private val selectedProjectPath: IOptPropertyView<Path>
) : AnAction(
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

        presentation.isEnabledAndVisible = lifetime.isAlive
            && sessionController.status.value != AvaloniaPreviewerSessionController.Status.Suspended
            && selectedProjectPath.hasValue
    }

    override fun actionPerformed(p0: AnActionEvent) {
        sessionController.start(selectedProjectPath.valueOrNull ?: return, executionMode = ProcessExecutionMode.Debug)
    }
}
