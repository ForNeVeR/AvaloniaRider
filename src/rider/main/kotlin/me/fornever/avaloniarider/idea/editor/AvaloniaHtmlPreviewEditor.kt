package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rider.xaml.splitEditor.XamlSplitEditor
import me.fornever.avaloniarider.idea.editor.actions.OpenBrowserAction
import javax.swing.JComponent

class AvaloniaHtmlPreviewEditor(
    project: Project,
    currentFile: VirtualFile,
    parentEditor: XamlSplitEditor? = null
) : AvaloniaPreviewEditorBase(project, currentFile, parentEditor) {

    private val panel = lazy {
        HtmlPreviewEditorComponent(lifetime, sessionController)
    }

    private val openBrowserAction = OpenBrowserAction(lifetime, sessionController)

    override val editorComponent = panel.value
    override fun getExtraActions(): Array<AnAction> = arrayOf(openBrowserAction)
    override fun createToolbar(targetComponent: JComponent) = createToolbarComponent(
        targetComponent,
        false,
        openBrowserAction
    )
}
