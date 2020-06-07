package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class AvaloniaRemotePreviewEditor(
    project: Project,
    currentFile: VirtualFile
) : AvaloniaPreviewEditorBase(project, currentFile) {

    private val panel = lazy {
        sessionController.start()
        BitmapPreviewEditorComponent(lifetime, sessionController)
    }

    override fun getComponent() = panel.value
}
