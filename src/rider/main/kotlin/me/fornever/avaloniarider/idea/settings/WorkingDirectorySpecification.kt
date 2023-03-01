package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.project.Project
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.projectView.solutionDirectoryPath
import java.nio.file.Path
import kotlin.io.path.Path

sealed interface WorkingDirectorySpecification {
    fun getPath(project: Project, projectWorkingDirectory: Path): Path
    companion object {
        fun getWorkingDirectory(projectOutput: ProjectOutput): Path {
            val path = projectOutput.workingDirectory
            if (path.isNotEmpty()) return Path(path)
            return Path(projectOutput.exePath).parent
        }
    }
}

object DefinedByMsBuild : WorkingDirectorySpecification {
    override fun getPath(project: Project, projectWorkingDirectory: Path) = projectWorkingDirectory
}

object SolutionDirectory : WorkingDirectorySpecification {
    override fun getPath(project: Project, projectWorkingDirectory: Path) = project.solutionDirectoryPath
}

data class CustomPath(val path: String) : WorkingDirectorySpecification {
    override fun getPath(project: Project, projectWorkingDirectory: Path) = Path(path)
}
