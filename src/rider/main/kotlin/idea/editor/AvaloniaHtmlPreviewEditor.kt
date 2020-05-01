package me.fornever.avaloniarider.idea.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rd.util.lifetime.isAlive
import java.net.URI

class AvaloniaHtmlPreviewEditor(
    project: Project,
    currentFile: VirtualFile
) : AvaloniaPreviewEditorBase(project, currentFile) {

    private val panel = lazy {
        sessionController.start(currentFile)
        HtmlPreviewEditorComponent(lifetime, sessionController)
    }

    inner class OpenBrowserAction : AnAction(
        "Open in Browser",
        "Open the current previewer session in a browser",
        AllIcons.General.Web
    ), DumbAware {
        private var currentUri: URI? = null
        init {
            sessionController.htmlTransportStarted.advise(lifetime) {
                application.invokeLater {
                    currentUri = URI(it.uri)
                }
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = lifetime.isAlive && currentUri != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            currentUri?.let {
                BrowserLauncher.instance.browse(it)
            }
        }
    }

    override fun getComponent() = panel.value
    override fun customizeEditorToolbar(group: DefaultActionGroup) {
        group.add(OpenBrowserAction())
    }
}
