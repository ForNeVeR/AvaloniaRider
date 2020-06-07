package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isAlive
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import java.net.URI

class OpenBrowserAction(
    private val lifetime: Lifetime,
    sessionController: AvaloniaPreviewerSessionController
) : AnAction(
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
