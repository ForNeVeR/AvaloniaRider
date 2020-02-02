package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class AvaloniaPreviewEditorProvider : FileEditorProvider {
    override fun getEditorTypeId() = "AvaloniaPreviewerEditor"
    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

    override fun accept(project: Project, file: VirtualFile) =
            file.extension == "xaml" // TODO: Backend XAML file check

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return AvaloniaPreviewEditor()
    }
}
