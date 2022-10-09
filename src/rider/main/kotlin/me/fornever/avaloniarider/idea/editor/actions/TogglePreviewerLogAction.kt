package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.jetbrains.rd.util.reactive.IProperty

class TogglePreviewerLogAction(private val isLogVisible: IProperty<Boolean>) : ToggleAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent) = isLogVisible.value
    override fun setSelected(e: AnActionEvent, value: Boolean) {
        isLogVisible.value = value
    }
}
