package me.fornever.avaloniarider.rider

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rd.ide.model.RdGetProjectOutputArgs
import com.jetbrains.rd.ide.model.RdProjectOutput
import com.jetbrains.rd.ide.model.riderProjectOutputModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fornever.avaloniarider.idea.concurrency.ApplicationAnyModality
import me.fornever.avaloniarider.idea.concurrency.await

@Service
class RiderProjectOutputHost(private val project: Project) {
    companion object {
        fun getInstance(project: Project): RiderProjectOutputHost =
            project.getService(RiderProjectOutputHost::class.java)
    }

    suspend fun getProjectOutput(lifetime: Lifetime, projectNode: ProjectModelEntity): RdProjectOutput =
        withContext(Dispatchers.ApplicationAnyModality) {
            val model = project.solution.riderProjectOutputModel
            @Suppress("UnstableApiUsage") val projectFilePath = projectNode.url!!.toPath().toString()
            model.getProjectOutput.start(lifetime, RdGetProjectOutputArgs(projectFilePath)).await(lifetime)
        }
}
