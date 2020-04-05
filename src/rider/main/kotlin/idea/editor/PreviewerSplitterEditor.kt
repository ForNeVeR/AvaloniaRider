package me.fornever.avaloniarider.idea.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.JBSplitter
import javax.swing.JComponent

/**
 * Component that shows two editors side by side.
 */
internal class PreviewerSplitterEditor(editor: TextEditor, preview: FileEditor)
    : TextEditorWithPreview(editor, preview, PreviewerSplitterEditor::javaClass.name) {

    companion object {
        private fun getSplitterFromRootComponent(component: JComponent): JBSplitter =
            component.getComponent(0) as JBSplitter
    }

    private fun getBooleanProperty(propertyName: String): Boolean =
        PropertiesComponent.getInstance().getBoolean("$name.$propertyName")
    private fun setBooleanProperty(propertyName: String, value: Boolean) =
        PropertiesComponent.getInstance().setValue("$name.$propertyName", value)

    private var splitter: JBSplitter? = null
    private var isSplitterHorizontal: Boolean
        get() = getBooleanProperty("isSplitterHorizontal")
        set(value) = setBooleanProperty("isSplitterHorizontal", value)

    override fun getComponent(): JComponent {
        val component = super.getComponent()
        if (splitter == null) {
            val splitter = getSplitterFromRootComponent(component)
            splitter.orientation = isSplitterHorizontal
            this.splitter = splitter
        }

        return component
    }

    inner class UseHorizontalSplitterAction : ToggleAction(
        "Use horizontal splitter",
        null,
        AllIcons.Actions.SplitHorizontally
    ), DumbAware {
        override fun isSelected(event: AnActionEvent) = isSplitterHorizontal
        override fun setSelected(event: AnActionEvent, value: Boolean) {
            isSplitterHorizontal = value
            splitter?.orientation = value
        }
    }

    override fun createViewActionGroup(): ActionGroup {
        val group = super.createViewActionGroup() as DefaultActionGroup
        group.add(UseHorizontalSplitterAction())
        return group
    }
}
