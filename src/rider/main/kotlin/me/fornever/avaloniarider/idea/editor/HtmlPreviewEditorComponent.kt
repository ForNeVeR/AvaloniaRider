package me.fornever.avaloniarider.idea.editor

import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.jetbrains.rd.util.lifetime.Lifetime
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import java.awt.BorderLayout
import javax.swing.JPanel

class HtmlPreviewEditorComponent(lifetime: Lifetime, controller: AvaloniaPreviewerSessionController): JPanel() {

    private var browser: JBCefBrowser? = null
    init {
        layout = BorderLayout()

        if (JBCefApp.isSupported()) {
            browser = JBCefBrowser().apply {
                add(component, BorderLayout.CENTER)
            }
        } else {
            add(JBLabel(AvaloniaRiderBundle.message("previewer.error.jcef-not-supported")), BorderLayout.CENTER)
        }

        controller.htmlTransportStarted.advise(lifetime) {
            connectTo(it.uri)
        }
    }

    private fun connectTo(uri: String) {
        browser?.loadURL(uri)
    }
}
