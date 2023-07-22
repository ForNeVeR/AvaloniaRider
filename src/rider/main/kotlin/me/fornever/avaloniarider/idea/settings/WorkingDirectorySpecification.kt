package me.fornever.avaloniarider.idea.settings

import com.intellij.openapi.project.Project
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.projectView.solutionDirectoryPath
import java.nio.file.Path
import kotlin.io.path.Path

enum class WorkingDirectorySpecification {
    DefinedByMsBuild,
    SolutionDirectory
}

fun WorkingDirectorySpecification.getPath(project: Project, projectWorkingDirectory: Path): Path {
    return when(this) {
        WorkingDirectorySpecification.DefinedByMsBuild -> projectWorkingDirectory
        WorkingDirectorySpecification.SolutionDirectory -> project.solutionDirectoryPath
    }
}

fun ProjectOutput.getCorrectWorkingDirectory(): Path {
    val path = workingDirectory
    if (path.isNotEmpty()) return Path(path)
    return Path(exePath).parent
}
