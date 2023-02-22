package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.application
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rider.util.idea.getService
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * These are Avalonia workspace-tied settings, not generally meant to be shared across the team.
 */
class AvaloniaWorkspaceState : BaseState() {
    var projectPerEditor by map<String, String>()
}

@State(name = "AvaloniaProject", storages = [Storage("avalonia.xml")]) // TODO[#265]: move to a workspace-related file
@Service(Service.Level.PROJECT)
class AvaloniaWorkspaceSettings(private val project: Project) : SimplePersistentStateComponent<AvaloniaWorkspaceState>(
    AvaloniaWorkspaceState()
) {
    companion object {
        fun getInstance(project: Project): AvaloniaWorkspaceSettings = project.getService()
    }

    private fun getProjectRelativeSystemIndependentPath(relativePath: Path): String {
        val basePath = project.basePath ?: return relativePath.systemIndependentPath
        val resultPath = FileUtil.getRelativePath(File(basePath), relativePath.toFile())
            ?: return relativePath.systemIndependentPath
        return Paths.get(resultPath).systemIndependentPath
    }

    fun storeSelection(xamlFilePath: Path, projectFilePath: Path) {
        application.assertIsDispatchThread()

        val relativeXamlPath = getProjectRelativeSystemIndependentPath(xamlFilePath)
        val relativeProjectPath = getProjectRelativeSystemIndependentPath(projectFilePath)
        state.projectPerEditor[relativeXamlPath] = relativeProjectPath
    }

    fun getSelection(xamlFilePath: Path): Path? {
        application.assertIsDispatchThread()

        val relativeXamlPath = getProjectRelativeSystemIndependentPath(xamlFilePath)
        val relativeProjectPath = state.projectPerEditor[relativeXamlPath] ?: return null
        val basePath = project.basePath ?: return Paths.get(relativeProjectPath)
        return Paths.get(basePath, relativeProjectPath)
    }
}
