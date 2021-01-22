package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
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
        file.extension == "xaml" || file.extension == "paml" || file.extension == "axaml" // TODO: Backend XAML file check (#42)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        val previewerEditor = when (AvaloniaSettings.getInstance(project).previewerTransportType) {
            AvaloniaPreviewerMethod.AvaloniaRemote -> AvaloniaRemotePreviewEditor(project, file)
            AvaloniaPreviewerMethod.Html -> AvaloniaHtmlPreviewEditor(project, file)
        }
        return PreviewerSplitterEditor(textEditor, previewerEditor)
    }
}
