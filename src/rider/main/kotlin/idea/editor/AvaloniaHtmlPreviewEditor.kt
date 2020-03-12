package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class AvaloniaHtmlPreviewEditor(
    project: Project,
    currentFile: VirtualFile
) : AvaloniaPreviewEditorBase(project, currentFile) {

    private val panel = lazy {
        sessionController.start(currentFile)
        HtmlPreviewEditorComponent(lifetime, sessionController)
    }

    override fun getComponent() = panel.value
}
