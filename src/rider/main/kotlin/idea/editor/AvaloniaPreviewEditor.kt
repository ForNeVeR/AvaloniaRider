package me.fornever.avaloniarider.idea.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import java.beans.PropertyChangeListener
import javax.swing.JLabel
import javax.swing.JPanel

class AvaloniaPreviewEditor : UserDataHolderBase(), FileEditor {
    override fun getName() = "Previewer"
    override fun isValid() = true

    private val panel = lazy { JPanel().apply { add(JLabel("Avalonia Previewer Placeholder")) } }

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
    override fun dispose() {}
}
