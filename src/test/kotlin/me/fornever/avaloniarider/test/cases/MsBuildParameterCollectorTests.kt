package me.fornever.avaloniarider.test.cases

import com.intellij.workspaceModel.ide.WorkspaceModel
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldContains
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import me.fornever.avaloniarider.model.RdProjectOutput
import me.fornever.avaloniarider.previewer.MsBuildParameterCollector
import me.fornever.avaloniarider.test.framework.LongLoadingTest
import me.fornever.avaloniarider.test.framework.runPumping
import org.testng.annotations.Test

class MsBuildParameterCollectorTests : LongLoadingTest() {

    override fun getSolutionDirectoryName() = "MSBuildParameters"

    @Suppress("UnstableApiUsage")
    @Test
    fun testLaunchSettingsParametersCollection() {
        val collector = MsBuildParameterCollector.getInstance(project)
        val workspaceModel = WorkspaceModel.getInstance(project)
        val runnableProjects = project.solution.runnableProjectsModel.projects.valueOrNull.shouldNotBeNull()

        runnableProjects.shouldContains { it.kind == RunnableProjectKinds.DotNetCore }
        runnableProjects.shouldContains { it.kind == RunnableProjectKinds.LaunchSettings }

        val projectFilePath = activeSolutionDirectory.toPath().resolve("MSBuildParameters.csproj")
        val projectOutput = runnableProjects.single { it.kind == RunnableProjectKinds.DotNetCore }
            .projectOutputs.single()
        val projectModelEntity = workspaceModel.getProjectModelEntities(projectFilePath, project).single()
        val parameters = runPumping {
            collector.getAvaloniaPreviewerParameters(
                projectFilePath,
                RdProjectOutput(projectOutput.tfm.shouldNotBeNull(), projectOutput.exePath),
                projectModelEntity
            )
        }

        val previewerPathForTest = activeSolutionDirectory.toPath().resolve("PreviewerForTest.exe")
        parameters.previewerBinary.shouldBe(previewerPathForTest)
    }
}
