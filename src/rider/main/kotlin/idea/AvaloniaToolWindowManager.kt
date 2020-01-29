package me.fornever.avaloniarider.idea

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager

class AvaloniaToolWindowManager(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AvaloniaToolWindowManager =
                ServiceManager.getService(project, AvaloniaToolWindowManager::class.java)
    }

    val toolWindow = lazy {
        ToolWindowManager.getInstance(project).registerToolWindow(
                "Avalonia",
                true,
                ToolWindowAnchor.BOTTOM)
    }
}
