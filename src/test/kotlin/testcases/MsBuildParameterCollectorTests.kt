package testcases

import com.intellij.workspaceModel.ide.WorkspaceModel
import com.jetbrains.rd.ide.model.RdProjectOutput
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldContains
import com.jetbrains.rider.test.asserts.shouldNotBeNull
import com.jetbrains.rider.test.base.BaseTestWithSolution
import me.fornever.avaloniarider.previewer.MsBuildParameterCollector
import me.fornever.avaloniarider.tests.framework.runPumping
import org.testng.annotations.Test

class MsBuildParameterCollectorTests : BaseTestWithSolution() {

    override fun getSolutionDirectoryName() = "MSBuildParameters"

    @Suppress("UnstableApiUsage")
    @Test
    fun testLaunchSettingsParametersCollection() {
        val collector = MsBuildParameterCollector.getInstance(project)
        val workspaceModel = WorkspaceModel.getInstance(project)
        val runnableProjects = project.solution.runnableProjectsModel.projects.valueOrNull.shouldNotBeNull()

        runnableProjects.shouldContains { it.kind == RunnableProjectKind.DotNetCore }
        runnableProjects.shouldContains { it.kind == RunnableProjectKind.LaunchSettings }

        val projectFilePath = activeSolutionDirectory.toPath().resolve("MSBuildParameters.csproj")
        val projectOutput = runnableProjects.single { it.kind == RunnableProjectKind.DotNetCore }
            .projectOutputs.single()
        val projectModelEntity = workspaceModel.getProjectModelEntities(projectFilePath, project).single()
        val parameters = runPumping {
            Lifetime.using { lt ->
                collector.getAvaloniaPreviewerParameters(
                    projectFilePath,
                    RdProjectOutput(projectOutput.tfm.shouldNotBeNull(), projectOutput.exePath),
                    projectModelEntity
                )
            }
        }

        val previewerPathForTest = activeSolutionDirectory.toPath().resolve("PreviewerForTest.exe")
        parameters.previewerBinary.shouldBe(previewerPathForTest)
    }
}
