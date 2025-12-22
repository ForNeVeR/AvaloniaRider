package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rider.xaml.splitEditor.XamlSplitEditor
import me.fornever.avaloniarider.idea.editor.actions.ZoomLevelSelectorAction
import me.fornever.avaloniarider.idea.settings.AvaloniaProjectSettings
import javax.swing.JComponent

class AvaloniaRemotePreviewEditor(
    project: Project,
    currentFile: VirtualFile,
    parentEditor: XamlSplitEditor? = null
) : AvaloniaPreviewEditorBase(project, currentFile, parentEditor) {

    private val panel = lazy {
        BitmapPreviewEditorComponent(lifetime, sessionController, AvaloniaProjectSettings.getInstance(project))
    }

    private val zoomAction = ZoomLevelSelectorAction(sessionController.zoomFactor)

    override val editorComponent = panel.value
    override fun getExtraActions(): Array<AnAction> = arrayOf(zoomAction)
    override fun createToolbar(targetComponent: JComponent) =
        createToolbarComponent(targetComponent, false, zoomAction)
}
