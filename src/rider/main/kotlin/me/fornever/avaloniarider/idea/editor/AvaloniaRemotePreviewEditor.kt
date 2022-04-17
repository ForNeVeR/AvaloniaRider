package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings

class AvaloniaRemotePreviewEditor(
    project: Project,
    currentFile: VirtualFile
) : AvaloniaPreviewEditorBase(project, currentFile) {

    private val panel = lazy {
        BitmapPreviewEditorComponent(lifetime, sessionController, AvaloniaSettings.getInstance(project))
    }

    override val editorComponent = panel.value
    override val toolbarComponent = createToolbarComponent()
}
