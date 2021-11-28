package me.fornever.avaloniarider.idea.editor

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

    override val editorComponent = panel.value
    override val toolbarComponent = createToolbarComponent(
        OpenBrowserAction(lifetime, sessionController)
    )
}
