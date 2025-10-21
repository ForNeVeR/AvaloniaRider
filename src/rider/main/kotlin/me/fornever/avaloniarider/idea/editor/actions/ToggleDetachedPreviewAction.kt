package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewEditorBase

class ToggleDetachedPreviewAction(
    private val previewEditor: AvaloniaPreviewEditorBase
) : ToggleAction(
    AvaloniaRiderBundle.messagePointer("action.previewer.detach"),
    AllIcons.Actions.MoveToWindow
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent) = previewEditor.isPreviewDetached()

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) {
            previewEditor.detachPreviewToWindow()
        } else {
            previewEditor.attachPreviewToEditor()
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val isDetached = previewEditor.isPreviewDetached()
        val textKey = if (isDetached) "action.previewer.attach" else "action.previewer.detach"
        val descriptionKey = if (isDetached) "action.previewer.attach.description" else "action.previewer.detach.description"

        e.presentation.text = AvaloniaRiderBundle.message(textKey)
        e.presentation.description = AvaloniaRiderBundle.message(descriptionKey)
    }
}
