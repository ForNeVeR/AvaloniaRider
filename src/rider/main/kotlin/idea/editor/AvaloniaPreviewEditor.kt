package me.fornever.avaloniarider.idea.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import java.beans.PropertyChangeListener

class AvaloniaPreviewEditor(
    project: Project,
    private val currentFile: VirtualFile
) : UserDataHolderBase(), FileEditor {

    override fun getName() = "Previewer"
    override fun isValid() = true

    override fun getFile() = currentFile

    private val lifetime = LifetimeDefinition()
    private val sessionController = AvaloniaPreviewerSessionController(project, lifetime)
    private val panel = lazy {
        sessionController.start(currentFile)
        AvaloniaPreviewEditorComponent(lifetime, sessionController)
    }

    override fun getComponent() = panel.value
    override fun getPreferredFocusedComponent() = component

    override fun isModified() = false
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun setState(state: FileEditorState) {}
    override fun selectNotify() {}
    override fun deselectNotify() {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
    override fun dispose() {
        lifetime.terminate()
    }
}
