package me.fornever.avaloniarider.idea.editor.legacy

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.fornever.avaloniarider.idea.editor.BitmapPreviewEditorComponent

class AvaloniaRemotePreviewEditor(
    project: Project,
    currentFile: VirtualFile
) : AvaloniaPreviewEditorBase(project, currentFile) {

    private val panel = lazy {
        BitmapPreviewEditorComponent(lifetime, sessionController)
    }

    override fun getComponent() = panel.value
}
