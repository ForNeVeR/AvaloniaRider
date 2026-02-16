package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rider.xaml.splitEditor.editorActions.SwapPreviewAndTextEditor
import com.jetbrains.rider.xaml.splitEditor.editorActions.XamlSplitEditorActionsUtils

class SwapPreviewAndDetachGroup : DefaultActionGroup(), DumbAware {
    private val swapAction = SwapPreviewAndTextEditor()
    private val detachAction = ToggleDetachedPreviewInToolbarAction()

    init {
        setPopup(false)
        templatePresentation.apply {
            text = swapAction.templatePresentation.text
            description = swapAction.templatePresentation.description
            icon = swapAction.templatePresentation.icon
        }
        add(swapAction)
        add(detachAction)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        swapAction.actionPerformed(e)
    }

    override fun update(e: AnActionEvent) {
        val splitEditor = XamlSplitEditorActionsUtils.getSplitEditorFromEvent(e)
        e.presentation.isEnabledAndVisible = splitEditor != null
    }
}
