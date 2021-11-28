package me.fornever.avaloniarider.rider

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.withUiContext
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.map
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import me.fornever.avaloniarider.model.RdGetProjectOutputArgs
import me.fornever.avaloniarider.model.RdProjectOutput
import me.fornever.avaloniarider.model.avaloniaRiderProjectModel
import java.nio.file.Path

@Service
class AvaloniaRiderProjectModelHost(private val project: Project) {
    companion object {
        fun getInstance(project: Project): AvaloniaRiderProjectModelHost =
            project.getService(AvaloniaRiderProjectModelHost::class.java)
    }

    suspend fun getProjectOutput(lifetime: Lifetime, projectFilePath: Path): RdProjectOutput =
        withUiContext(lifetime) {
            val model = project.solution.avaloniaRiderProjectModel
            model.getProjectOutput.startSuspending(lifetime, RdGetProjectOutputArgs(projectFilePath.toString()))
        }

    val filteredRunnableProjects: IOptPropertyView<Sequence<RunnableProject>> by lazy {
        project.solution.runnableProjectsModel.projects
            .map { projects ->
                projects
                    .asSequence()
                    .filter { it.kind == RunnableProjectKind.DotNetCore || it.kind == RunnableProjectKind.Console }
                    .sortedBy { it.kind != RunnableProjectKind.DotNetCore }
                    .distinctBy { it.projectFilePath }
            }
    }
}
