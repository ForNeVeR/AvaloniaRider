package me.fornever.avaloniarider.testcases

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.valueOrThrow
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.test.asserts.shouldBe
import com.jetbrains.rider.test.base.BaseTestWithSolution
import me.fornever.avaloniarider.idea.editor.actions.RunnableAssemblySelectorAction
import org.testng.Assert.assertFalse
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
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

    @Suppress("UnstableApiUsage")
    private val workspaceModel
        get() = WorkspaceModel.getInstance(project)

    @Test
    fun actionEnabledTests() {
        val isSolutionLoading = OptProperty(true)
        val action = RunnableAssemblySelectorAction(
            testLifetime,
            project,
            workspaceModel,
            isSolutionLoading,
            OptProperty()
        )
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
        val action = RunnableAssemblySelectorAction(
            testLifetime,
            project,
            workspaceModel,
            OptProperty(false),
            runnableProjects
        )
        val group = action.popupActionGroup

        group.getChildren(null).size.shouldBe(0)

        val project = createTestProject()
        runnableProjects.set(listOf(project))

        val children = group.getChildren(null)
        children.size.shouldBe(1)
        children[0].templatePresentation.text.shouldBe(project.name)
    }

    @Test
    fun firstAssemblyShouldBeSelectedAutomatically() {
        val runnableProjects = OptProperty(emptyList<RunnableProject>())
        val action = RunnableAssemblySelectorAction(
            testLifetime,
            project,
            workspaceModel,
            OptProperty(false),
            runnableProjects
        )

        val project = createTestProject()
        runnableProjects.set(listOf(project))

        action.selectedProjectPath.valueOrThrow.systemIndependentPath.shouldBe(project.projectFilePath)
    }
}
