package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rd.ide.model.AvaloniaRiderProjectModel
import com.jetbrains.rd.ide.model.RdGetReferencingProjectsRequest
import com.jetbrains.rd.ide.model.avaloniaRiderProjectModel
import com.jetbrains.rd.platform.util.getLogger
import com.jetbrains.rd.platform.util.launchOnUi
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.map
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.calculateIcon
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.isProject
import com.jetbrains.rider.projectView.workspace.isUnloadedProject
import me.fornever.avaloniarider.AvaloniaRiderBundle.message
import me.fornever.avaloniarider.rd.compose
import me.fornever.avaloniarider.rider.getProjectContainingFile
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.nameWithoutExtension

@Suppress("UnstableApiUsage")
class RunnableAssemblySelectorAction(
    private val lifetime: Lifetime,
    private val project: Project,
    private val workspaceModel: WorkspaceModel,
    private val avaloniaRiderModel: AvaloniaRiderProjectModel,
    isSolutionLoading: IOptPropertyView<Boolean>,
    runnableProjects: IOptPropertyView<List<RunnableProject>>,
    private val xamlFile: VirtualFile
) : ComboBoxAction() {

    companion object {
        private val logger = getLogger<RunnableAssemblySelectorAction>()
    }

    constructor(lifetime: Lifetime, project: Project, xamlFile: VirtualFile) : this(
        lifetime,
        project,
        WorkspaceModel.getInstance(project),
        project.solution.avaloniaRiderProjectModel,
        project.solution.isLoading,
        project.solution.runnableProjectsModel.projects,
        xamlFile
    )

    private val isProcessingProjectList = Property(false)
    private val isLoading = compose(isSolutionLoading, isProcessingProjectList)
        .map { (isSolutionLoading, isProcessing) -> (isSolutionLoading ?: true) || isProcessing }

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
        val isLoading = isLoading.value
        e.presentation.apply {
            isEnabled = !isLoading
            icon = calculateIcon(selectedProject)

            @Suppress("DialogTitleCapitalization")
            text =
                if (isLoading) message("assemblySelector.loading")
                else selectedProject?.name ?: message("assemblySelector.unableToDetermineProject")
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun fillWithActions(runnableProjects: List<RunnableProject>) {
        lifetime.launchOnUi {
            isProcessingProjectList.set(true)
            try {
                val targetFileProjectEntity = xamlFile.getProjectContainingFile(lifetime, project)
                val targetFileProjectPath = targetFileProjectEntity.url!!.toPath()
                val runnableProjectPaths = runnableProjects.map { it.projectFilePath }
                val refRequest = RdGetReferencingProjectsRequest(targetFileProjectPath.toString(), runnableProjectPaths)

                logger.info("Calculating referencing projects for project ${targetFileProjectPath.nameWithoutExtension} among ${runnableProjectPaths.size} runnable projects")
                val actuallyReferencedProjects = avaloniaRiderModel.getReferencingProjects.startSuspending(
                    lifetime,
                    refRequest
                )
                logger.info("There are ${actuallyReferencedProjects.size} projects referencing ${targetFileProjectPath.nameWithoutExtension} among the passed ones")

                val runnableProjectPerPath =
                    runnableProjects.map { r -> Paths.get(r.projectFilePath).systemIndependentPath to r }.toMap()

                popupActionGroup.removeAll()
                val selectableRunnableProjects = actuallyReferencedProjects.map { path ->
                    val normalizedPath = Paths.get(path).systemIndependentPath
                    val runnableProject = runnableProjectPerPath[normalizedPath]
                    if (runnableProject == null) {
                        logger.error("Couldn't find runnable project for path $normalizedPath")
                    }
                    runnableProject
                }.filterNotNull().sortedBy { it.name }

                for (runnableProject in selectableRunnableProjects) {
                    popupActionGroup.add(object : DumbAwareAction(
                        { runnableProject.name },
                        calculateIcon(runnableProject)
                    ) {
                        override fun actionPerformed(event: AnActionEvent) {
                            selectedRunnableProjectProperty.set(runnableProject)
                        }
                    })
                }

                if (selectedRunnableProjectProperty.valueOrNull == null && selectableRunnableProjects.isNotEmpty()) {
                    selectedRunnableProjectProperty.set(selectableRunnableProjects.first())
                }
            } finally {
                isProcessingProjectList.set(false)
            }
        }
    }
}
