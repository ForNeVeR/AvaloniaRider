package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.fornever.avaloniarider.idea.editor.actions.OpenBrowserAction
import javax.swing.JComponent

class AvaloniaHtmlPreviewEditor(
    project: Project,
    currentFile: VirtualFile
) : AvaloniaPreviewEditorBase(project, currentFile) {

    private val panel = lazy {
        HtmlPreviewEditorComponent(lifetime, sessionController)
    }

    override val editorComponent = panel.value
    override fun createToolbar(targetComponent: JComponent) = createToolbarComponent(
        targetComponent,
        OpenBrowserAction(lifetime, sessionController)
    )
}
