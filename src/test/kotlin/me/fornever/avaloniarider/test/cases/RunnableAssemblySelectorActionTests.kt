package me.fornever.avaloniarider.test.cases

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.util.io.systemIndependentPath
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.hasValue
import com.jetbrains.rd.util.reactive.valueOrThrow
import com.jetbrains.rdclient.util.idea.pumpMessages
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.configurations.RunnableProjectKinds
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.asserts.shouldBeTrue
import com.jetbrains.rider.test.asserts.shouldContains
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.scriptingApi.getVirtualFileFromPath
import me.fornever.avaloniarider.idea.editor.actions.RunnableAssemblySelectorAction
import me.fornever.avaloniarider.idea.settings.AvaloniaProjectSettings
import me.fornever.avaloniarider.idea.settings.AvaloniaSettings
import me.fornever.avaloniarider.model.avaloniaRiderProjectModel
import me.fornever.avaloniarider.test.framework.AvaloniaIntegrationTest
import me.fornever.avaloniarider.test.framework.canonicalSolutionDirectory
import org.testng.Assert.assertFalse
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.nio.file.Paths
import java.time.Duration
import kotlin.test.assertTrue

@TestEnvironment(sdkVersion = SdkVersion.AUTODETECT, buildTool = BuildTool.AUTODETECT)
class RunnableAssemblySelectorActionTests : AvaloniaIntegrationTest() {
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

    private val testXamlFile
        get() = getVirtualFileFromPath("ClassLibrary1/MyControl.axaml", canonicalSolutionDirectory.toFile())

    private fun createMockSelector(
        isSolutionLoading: IOptPropertyView<Boolean> = OptProperty(),
        runnableProjects: IOptPropertyView<Sequence<RunnableProject>> = OptProperty()
    ): RunnableAssemblySelectorAction {
        return RunnableAssemblySelectorAction(
            testLifetime,
            project,
            WorkspaceModel.getInstance(project),
            project.messageBus,
            RunManager.getInstance(project),
            AvaloniaSettings.getInstance(project),
            AvaloniaProjectSettings.getInstance(project),
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
        val runnableProjects = OptProperty(emptySequence<RunnableProject>())
        val action = createMockSelector(OptProperty(false), runnableProjects)
        val group = action.popupActionGroup

        group.getChildren(null).size.shouldBe(0)

        val project = project.solution.runnableProjectsModel.projects.valueOrThrow.single {
            it.name == "AvaloniaApp1" && it.kind == RunnableProjectKinds.DotNetCore
        }
        runnableProjects.set(sequenceOf(project))
        pumpMessages(Duration.ofSeconds(5L)) { !action.isLoading.value }.shouldBeTrue()

        val children = group.getChildren(null)
        children.size.shouldBe(1)
        children[0].templateText.shouldBe(project.name)
    }

    @Test
    fun firstAssemblyShouldBeSelectedAutomatically() {
        val action = createSelector()
        pumpMessages(Duration.ofSeconds(5L)) { action.selectedProjectPath.hasValue }.shouldBeTrue()

        val expectedPath = project.solution.runnableProjectsModel.projects.valueOrThrow
            .single { it.name == "AvaloniaApp1" && it.kind == RunnableProjectKinds.DotNetCore }
            .projectFilePath.let(Paths::get).systemIndependentPath
        action.selectedProjectPath.valueOrThrow.systemIndependentPath.shouldBe(expectedPath)
    }

    @Test
    fun onlyReferencedAssembliesShouldBeAvailable() {
        val runnableProjectsModel = project.solution.runnableProjectsModel
        pumpMessages { runnableProjectsModel.projects.valueOrNull?.isNotEmpty() ?: false }
        runnableProjectsModel.projects.valueOrThrow
            .filter { it.kind == RunnableProjectKinds.DotNetCore || it.kind == RunnableProjectKinds.Console }
            .size.shouldBe(3)

        val action = createSelector()
        pumpMessages(Duration.ofSeconds(5L)) { !action.isLoading.value }.shouldBeTrue()

        val items = action.popupActionGroup.getChildren(null).map { it.templateText }
        items.size.shouldBe(2)
        items.shouldContains { it == "AvaloniaApp1" }
        items.shouldContains { it == "AvaloniaApp2" }
    }

    @TestEnvironment(solution = "AvaloniaMvvm")
    @Test
    fun targetProjectItselfShouldBeAvailable() {
        val runnableProjectsModel = project.solution.runnableProjectsModel
        pumpMessages { runnableProjectsModel.projects.valueOrNull?.isNotEmpty() ?: false }

        val action = RunnableAssemblySelectorAction(
            testLifetime,
            project,
            getVirtualFileFromPath("Views/MainWindow.xaml", canonicalSolutionDirectory.toFile())
        )
        pumpMessages(Duration.ofSeconds(5L)) { !action.isLoading.value }.shouldBeTrue()

        val items = action.popupActionGroup.getChildren(null).map { it.templateText }
        items.size.shouldBe(1)
        items.shouldContains { it == "AvaloniaMvvm" }
    }
}
