package me.fornever.avaloniarider.idea

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager

// TODO[F]: Get rid of this window manager (#39)
class AvaloniaToolWindowManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AvaloniaToolWindowManager = project.service()
    }

    val toolWindow = lazy {
        ToolWindowManager.getInstance(project).registerToolWindow(
            RegisterToolWindowTask("Avalonia", ToolWindowAnchor.BOTTOM, canCloseContent = true)
        )
    }
}
