package me.fornever.avaloniarider.rider

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.ide.model.RdGetProjectOutputArgs
import com.jetbrains.rd.ide.model.RdProjectOutput
import com.jetbrains.rd.ide.model.riderProjectOutputModel
import com.jetbrains.rd.platform.util.withUiContext
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.projectView.solution
import me.fornever.avaloniarider.idea.concurrency.await
import java.nio.file.Path

@Service
class RiderProjectOutputHost(private val project: Project) {
    companion object {
        fun getInstance(project: Project): RiderProjectOutputHost =
            project.getService(RiderProjectOutputHost::class.java)
    }

    suspend fun getProjectOutput(lifetime: Lifetime, projectFilePath: Path): RdProjectOutput =
        withUiContext(lifetime) {
            val model = project.solution.riderProjectOutputModel
            model.getProjectOutput.start(lifetime, RdGetProjectOutputArgs(projectFilePath.toString())).await(lifetime)
        }
}
