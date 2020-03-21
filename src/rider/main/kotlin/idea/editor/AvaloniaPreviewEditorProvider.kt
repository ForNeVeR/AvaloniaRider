package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerMethod
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings

class AvaloniaPreviewEditorProvider : FileEditorProvider, DumbAware {
    override fun getEditorTypeId() = "AvaloniaPreviewerEditor"
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun accept(project: Project, file: VirtualFile) =
        file.extension == "xaml" // TODO: Backend XAML file check (#42)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        val previewerEditor = when (AvaloniaSettings.getInstance(project).previewerTransportType) {
            AvaloniaPreviewerMethod.Socket -> AvaloniaRemotePreviewEditor(project, file)
            AvaloniaPreviewerMethod.Web -> AvaloniaHtmlPreviewEditor(project, file)
        }
        return TextEditorWithPreview(textEditor, previewerEditor)
    }
}
