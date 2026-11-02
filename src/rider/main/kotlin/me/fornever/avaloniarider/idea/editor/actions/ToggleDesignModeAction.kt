package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rd.util.reactive.IProperty
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerInteractionMode
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerMethod
import me.fornever.avaloniarider.idea.settings.AvaloniaProjectSettings

class ToggleDesignModeAction(
    private val interactionMode: IProperty<AvaloniaPreviewerInteractionMode>,
    private val settings: AvaloniaProjectSettings
) : ToggleAction(
    AvaloniaRiderBundle.messagePointer("action.previewer.design-mode.text"),
    AvaloniaRiderBundle.messagePointer("action.previewer.design-mode.description"),
    AllIcons.Actions.EditSource
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = settings.previewerTransportType == AvaloniaPreviewerMethod.AvaloniaRemote
    }

    override fun isSelected(e: AnActionEvent) = interactionMode.value == AvaloniaPreviewerInteractionMode.Design

    override fun setSelected(e: AnActionEvent, value: Boolean) {
        interactionMode.value =
            if (value) AvaloniaPreviewerInteractionMode.Design else AvaloniaPreviewerInteractionMode.Interactive
    }
}
