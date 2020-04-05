package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.ui.JBSplitter
import javax.swing.JComponent

/**
 * Component that shows two editors side by side.
 */
class PreviewerSplitterEditor(editor: TextEditor, preview: FileEditor)
    : TextEditorWithPreview(editor, preview, PreviewerSplitterEditor::javaClass.name) {

    companion object {
        private fun getSplitterFromRootComponent(component: JComponent): JBSplitter =
            component.getComponent(0) as JBSplitter
    }

    private var splitter: JBSplitter? = null

    override fun getComponent(): JComponent {
        val component = super.getComponent()
        if (splitter == null) {
            val splitter = getSplitterFromRootComponent(component)
            splitter.orientation = true // TODO[F]: Read from settings
            this.splitter = splitter
        }

        return component
    }
}
