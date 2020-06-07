package me.fornever.avaloniarider.idea.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import me.fornever.avaloniarider.idea.editor.actions.RestartPreviewerAction
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import java.beans.PropertyChangeListener
import javax.swing.JComponent

abstract class AvaloniaPreviewEditorBase(
    project: Project,
    private val currentFile: VirtualFile
) : UserDataHolderBase(), FileEditor {

    override fun getName() = "Preview"
    override fun isValid() = true

    override fun getFile() = currentFile

    private val lifetimeDefinition = LifetimeDefinition()
    protected val lifetime: Lifetime = lifetimeDefinition
    protected val sessionController = AvaloniaPreviewerSessionController(project, lifetime)

    abstract override fun getComponent(): JComponent
    override fun getPreferredFocusedComponent() = component

    open fun customizeEditorToolbar(group: DefaultActionGroup) {
        group.add(RestartPreviewerAction(lifetime, file, sessionController))
    }

    override fun isModified() = false
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun setState(state: FileEditorState) {}
    override fun selectNotify() {}
    override fun deselectNotify() {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
    override fun dispose() {
        lifetimeDefinition.terminate()
    }
}
