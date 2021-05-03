package me.fornever.avaloniarider.testcases

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.jetbrains.rd.ide.model.avaloniaRiderProjectModel
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.hasValue
import com.jetbrains.rd.util.reactive.valueOrThrow
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.asserts.shouldContains
import com.jetbrains.rider.test.base.BaseTestWithSolution
import me.fornever.avaloniarider.idea.editor.actions.RunnableAssemblySelectorAction
import org.testng.Assert.assertFalse
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.nio.file.Paths
import java.time.Duration
import kotlin.test.assertTrue

class RunnableAssemblySelectorActionTests : BaseTestWithSolution() {
    override fun getSolutionDirectoryName() = "MultiProjectSolution"

    private lateinit var testLifetime: LifetimeDefinition

    @BeforeMethod
    fun beforeMethod() {
        testLifetime = LifetimeDefinition()
    }

    @AfterMethod
    fun afterMethod() {
        testLifetime.terminate()
    }

    private fun createTestProject() = RunnableProject(
        "Project1",
        "Project1",
        "/tmp/Project1.csproj",
        RunnableProjectKind.DotNetCore,
        emptyList(),
        emptyList(),
        null,
        emptyList()
    )

    private val testXamlFile
        get() = VfsUtil.findFileByIoFile(
            tempTestDirectory.resolve("MultiProjectSolution/ClassLibrary1/MyControl.axaml"), true
        )!!

    private fun createMockSelector(
        isSolutionLoading: IOptPropertyView<Boolean> = OptProperty(),
        runnableProjects: IOptPropertyView<List<RunnableProject>> = OptProperty()
    ): RunnableAssemblySelectorAction {
        return RunnableAssemblySelectorAction(
            testLifetime,
            project,
            @Suppress("UnstableApiUsage") WorkspaceModel.getInstance(project),
            project.solution.avaloniaRiderProjectModel,
            isSolutionLoading,
            runnableProjects,
            testXamlFile
        )
    }

    private fun createSelector() =
        RunnableAssemblySelectorAction(testLifetime, project, testXamlFile)

    @Test
    fun actionEnabledTests() {
        val isSolutionLoading = OptProperty(true)
        val action = createMockSelector(isSolutionLoading)
        val dataContext = { _: Any -> null }
        val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
        val presentation = event.presentation

        action.update(event)
        assertFalse(presentation.isEnabled)

        isSolutionLoading.set(false)
        action.update(event)
        assertTrue(presentation.isEnabled)
    }

    @Test
    fun groupShouldBeFilledTest() {
        val runnableProjects = OptProperty(emptyList<RunnableProject>())
        val action = createMockSelector(OptProperty(false), runnableProjects)
        val group = action.popupActionGroup

        group.getChildren(null).size.shouldBe(0)

        val project = createTestProject()
        runnableProjects.set(listOf(project))

        val children = group.getChildren(null)
        children.size.shouldBe(1)
        children[0].templateText.shouldBe(project.name)
    }

    @Test
    fun firstAssemblyShouldBeSelectedAutomatically() {
        val action = createSelector()
        pumpMessages(Duration.ofSeconds(5L)) { action.selectedProjectPath.hasValue }.shouldBeTrue()

        val expectedPath = project.solution.runnableProjectsModel.projects.valueOrThrow
            .single { it.name == "AvaloniaApp1" }
            .projectFilePath.let(Paths::get).systemIndependentPath
        action.selectedProjectPath.valueOrThrow.systemIndependentPath.shouldBe(expectedPath)
    }

    @Test
    fun onlyReferencedAssembliesShouldBeAvailable() {
        pumpMessages { project.solution.runnableProjectsModel.projects.valueOrNull?.isNotEmpty() ?: false }

        val action = createSelector()
        val items = action.popupActionGroup.getChildren(null).map { it.templateText }
        items.size.shouldBe(2)
        items.shouldContains { it == "AvaloniaApp1" }
        items.shouldContains { it == "AvaloniaApp2" }
    }
}
