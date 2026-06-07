package me.fornever.avaloniarider.idea.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rider.model.PreviewPlatformKind
import com.jetbrains.rider.xaml.core.XamlPreviewEditor
import com.jetbrains.rider.xaml.core.XamlPreviewEditorExtension
import com.jetbrains.rider.xaml.splitEditor.XamlSplitEditor
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerMethod
import me.fornever.avaloniarider.idea.settings.AvaloniaProjectSettings

class AvaloniaPreviewerXamlEditorExtension : XamlPreviewEditorExtension {
    override fun accepts(file: VirtualFile, platform: PreviewPlatformKind): Boolean =
        platform == PreviewPlatformKind.AVALONIA

    override fun createEditor(
        project: Project,
        file: VirtualFile,
        parent: XamlSplitEditor,
        platform: PreviewPlatformKind
    ): XamlPreviewEditor = when (AvaloniaProjectSettings.getInstance(project).previewerTransportType) {
        AvaloniaPreviewerMethod.AvaloniaRemote -> AvaloniaRemotePreviewEditor(project, file, parent)
        AvaloniaPreviewerMethod.Html -> AvaloniaHtmlPreviewEditor(project, file, parent)
    }
}
