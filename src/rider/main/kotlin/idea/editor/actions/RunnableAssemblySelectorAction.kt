package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.calculateIcon
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.isProject
import com.jetbrains.rider.projectView.workspace.isUnloadedProject
import me.fornever.avaloniarider.AvaloniaRiderBundle.message
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent

@Suppress("UnstableApiUsage")
class RunnableAssemblySelectorAction(
    lifetime: Lifetime,
    private val project: Project,
    private val workspaceModel: WorkspaceModel,
    private val isSolutionLoading: IOptProperty<Boolean>,
    runnableProjects: IOptProperty<List<RunnableProject>>
) : ComboBoxAction() {
    constructor(lifetime: Lifetime, project: Project) : this(
        lifetime,
        project,
        WorkspaceModel.getInstance(project),
        project.solution.isLoading,
        project.solution.runnableProjectsModel.projects,
    )

    // TODO: Filter by only referenced assemblies
    // TODO: Persist user selection; base initial assembly guess on already persisted files from the current assembly
    val popupActionGroup: DefaultActionGroup = DefaultActionGroup()
    override fun createPopupActionGroup(button: JComponent?) = popupActionGroup

    private val selectedRunnableProjectProperty = OptProperty<RunnableProject>()
    val selectedProjectPath: IOptPropertyView<Path> = selectedRunnableProjectProperty.map { p ->
        Paths.get(p.projectFilePath)
    }

    init {
        runnableProjects.advise(lifetime, ::fillWithActions)
    }

    private fun calculateIcon(runnableProject: RunnableProject?) =
        runnableProject?.let { VfsUtil.findFile(Paths.get(it.projectFilePath), false) }?.let { virtualFile ->
            workspaceModel.getProjectModelEntities(virtualFile, project).singleOrNull {
                it.isProject() || it.isUnloadedProject()
            }
        }?.calculateIcon(project)

    override fun update(e: AnActionEvent) {
        val selectedProject = selectedRunnableProjectProperty.valueOrNull
        val isSolutionLoading = isSolutionLoading.valueOrDefault(true)
        e.presentation.apply {
            isEnabled = !isSolutionLoading
            icon = calculateIcon(selectedProject)

            @Suppress("DialogTitleCapitalization")
            text =
                if (isSolutionLoading) message("assemblySelector.loading")
                else selectedProject?.name ?: message("assemblySelector.unableToDetermineProject")
        }
    }

    private fun fillWithActions(runnableProjects: List<RunnableProject>) {
        popupActionGroup.removeAll()
        for (runnableProject in runnableProjects) {
            popupActionGroup.add(object : DumbAwareAction({ runnableProject.name }, calculateIcon(runnableProject)) {
                override fun actionPerformed(event: AnActionEvent) {
                    selectedRunnableProjectProperty.set(runnableProject)
                }
            })
        }

        if (selectedRunnableProjectProperty.valueOrNull == null && runnableProjects.any()) {
            selectedRunnableProjectProperty.set(runnableProjects.first())
        }
    }
}
