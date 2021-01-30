package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.fornever.avaloniarider.idea.editor.actions.OpenBrowserAction

class AvaloniaHtmlPreviewEditor(
    project: Project,
    currentFile: VirtualFile
) : AvaloniaPreviewEditorBase(project, currentFile) {

    private val panel = lazy {
        HtmlPreviewEditorComponent(lifetime, sessionController)
    }

    override fun getComponent() = panel.value
    override fun customizeEditorToolbar(group: DefaultActionGroup) {
        super.customizeEditorToolbar(group)
        group.add(OpenBrowserAction(lifetime, sessionController))
    }
}
