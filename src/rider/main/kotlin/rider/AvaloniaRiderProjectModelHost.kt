package me.fornever.avaloniarider.rider

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.ide.model.RdGetProjectOutputArgs
import com.jetbrains.rd.ide.model.RdProjectOutput
import com.jetbrains.rd.ide.model.avaloniaRiderProjectModel
import com.jetbrains.rd.platform.util.withUiContext
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.map
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import me.fornever.avaloniarider.idea.concurrency.await
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
            model.getProjectOutput.start(lifetime, RdGetProjectOutputArgs(projectFilePath.toString())).await(lifetime)
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
