package me.fornever.avaloniarider.idea.editor

import com.jetbrains.rd.util.lifetime.Lifetime
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSessionController
import java.awt.FlowLayout
import javax.swing.JPanel

class HtmlPreviewEditorComponent(lifetime: Lifetime, controller: AvaloniaPreviewerSessionController): JPanel() {

    private lateinit var webView: WebView
    init {
        layout = FlowLayout()

        add(JFXPanel().apply {
            Platform.runLater {
                webView = WebView()
                scene = Scene(webView)
            }
        })

        controller.htmlTransportStarted.advise(lifetime) {
            connectTo(it.uri)
        }
    }

    private fun connectTo(uri: String) {
        Platform.runLater {
            webView.engine.load(uri)
        }
    }
}
