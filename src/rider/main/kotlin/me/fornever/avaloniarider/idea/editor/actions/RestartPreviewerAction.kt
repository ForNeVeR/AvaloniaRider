package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.hasValue
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import java.nio.file.Path

class RestartPreviewerAction(
    private val lifetime: Lifetime,
    private val sessionController: AvaloniaPreviewerSessionController,
    private val selectedProjectPath: IOptPropertyView<Path>
) : AnAction(
    "Restart Previewer",
    "Restarts the previewer session for the current document",
    AllIcons.Actions.Restart
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = lifetime.isAlive
            && sessionController.status.value != AvaloniaPreviewerSessionController.Status.Suspended
            && selectedProjectPath.hasValue
    }

    override fun actionPerformed(event: AnActionEvent) {
        sessionController.start(selectedProjectPath.valueOrNull ?: return)
    }
}
