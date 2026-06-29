package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rider.xaml.splitEditor.XamlSplitEditorSplitLayout
import com.jetbrains.rider.xaml.splitEditor.editorActions.XamlSplitEditorActionsUtils
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.idea.editor.AvaloniaPreviewEditorBase

class ToggleDetachedPreviewInToolbarAction : ToggleAction(
    AvaloniaRiderBundle.messagePointer("action.previewer.detach"),
    AllIcons.Actions.MoveToWindow
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean =
        getPreviewEditor(e)?.isPreviewDetached() == true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val previewEditor = getPreviewEditor(e) ?: return
        if (state) {
            previewEditor.detachPreviewToWindow()
        } else {
            previewEditor.attachPreviewToEditor()
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val splitEditor = XamlSplitEditorActionsUtils.getSplitEditorFromEvent(e)
        if (splitEditor == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val previewEditor = splitEditor.previewEditor as? AvaloniaPreviewEditorBase
        val isDetached = previewEditor?.isPreviewDetached() == true
        val isVisible = !isDetached

        val textKey = if (isDetached) "action.previewer.attach" else "action.previewer.detach"
        val descriptionKey = if (isDetached) "action.previewer.attach.description" else "action.previewer.detach.description"

        e.presentation.text = AvaloniaRiderBundle.message(textKey)
        e.presentation.description = AvaloniaRiderBundle.message(descriptionKey)
        e.presentation.isVisible = isVisible
        e.presentation.isEnabled = previewEditor != null
    }

    private fun getPreviewEditor(e: AnActionEvent): AvaloniaPreviewEditorBase? {
        val splitEditor = XamlSplitEditorActionsUtils.getSplitEditorFromEvent(e) ?: return null
        return splitEditor.previewEditor as? AvaloniaPreviewEditorBase
    }
}
