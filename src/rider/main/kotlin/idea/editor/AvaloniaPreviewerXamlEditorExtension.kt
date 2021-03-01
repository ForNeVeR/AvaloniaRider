package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rider.xaml.XamlPreviewEditorExtension
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerMethod
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings

class AvaloniaPreviewerXamlEditorExtension(private val project: Project) : XamlPreviewEditorExtension {
    override fun accepts(file: VirtualFile) =
        file.extension == "xaml" || file.extension == "paml" || file.extension == "axaml" // TODO: Backend XAML file check (#42)

    override fun createEditor(file: VirtualFile) = when (AvaloniaSettings.getInstance(project).previewerTransportType) {
        AvaloniaPreviewerMethod.AvaloniaRemote -> AvaloniaRemotePreviewEditor(project, file)
        AvaloniaPreviewerMethod.Html -> AvaloniaHtmlPreviewEditor(project, file)
    }
}
