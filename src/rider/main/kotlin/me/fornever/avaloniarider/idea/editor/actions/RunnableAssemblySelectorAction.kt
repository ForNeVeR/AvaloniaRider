package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.util.application
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.messages.MessageBus
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.*
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.projectView.calculateIcon
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import com.jetbrains.rider.projectView.workspace.isProject
import com.jetbrains.rider.projectView.workspace.isUnloadedProject
import com.jetbrains.rider.run.configurations.IProjectBasedRunConfiguration
import kotlinx.coroutines.Dispatchers
import me.fornever.avaloniarider.AvaloniaRiderBundle.message
import me.fornever.avaloniarider.idea.settings.AvaloniaProjectSettings
import me.fornever.avaloniarider.idea.settings.AvaloniaWorkspaceSettings
import me.fornever.avaloniarider.model.AvaloniaRiderProjectModel
import me.fornever.avaloniarider.model.RdGetReferencingProjectsRequest
import me.fornever.avaloniarider.model.avaloniaRiderProjectModel
import me.fornever.avaloniarider.rd.compose
import me.fornever.avaloniarider.rider.AvaloniaRiderProjectModelHost
import me.fornever.avaloniarider.rider.getProjectContainingFile
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import kotlin.io.path.nameWithoutExtension

class RunnableAssemblySelectorAction(
    private val lifetime: Lifetime,
    private val project: Project,
    private val workspaceModel: WorkspaceModel,
    messageBus: MessageBus,
    private val runManager: RunManager,
    private val avaloniaProjectSettings: AvaloniaProjectSettings,
    private val avaloniaWorkspaceSettings: AvaloniaWorkspaceSettings,
    private val avaloniaRiderModel: AvaloniaRiderProjectModel,
    isSolutionLoading: IOptPropertyView<Boolean>,
    runnableProjects: IOptPropertyView<Sequence<RunnableProject>>,
    private val xamlFile: VirtualFile
) : ComboBoxAction(), DumbAware, TooltipDescriptionProvider {

    companion object {
        private val logger = logger<RunnableAssemblySelectorAction>()
    }

    constructor(lifetime: Lifetime, project: Project, xamlFile: VirtualFile) : this(
        lifetime,
        project,
        WorkspaceModel.getInstance(project),
        project.messageBus,
        RunManager.getInstance(project),
        AvaloniaProjectSettings.getInstance(project),
        AvaloniaWorkspaceSettings.getInstance(project),
        project.solution.avaloniaRiderProjectModel,
        project.solution.isLoading,
        AvaloniaRiderProjectModelHost.getInstance(project).filteredRunnableProjects,
        xamlFile
    )

    private val isProcessingProjectList = Property(false)
    val isLoading = compose(isSolutionLoading, isProcessingProjectList)
        .map { (isSolutionLoading, isProcessing) -> (isSolutionLoading ?: true) || isProcessing }

    private val availableProjects = Property<List<RunnableProject>>(emptyList())

    val popupActionGroup: DefaultActionGroup = DefaultActionGroup()
    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext) = popupActionGroup

    private val selectedRunnableProjectProperty = OptProperty<RunnableProject>()
    val selectedProjectPath: IOptPropertyView<Path> = selectedRunnableProjectProperty.map { p ->
        Paths.get(p.projectFilePath)
    }

    private fun updateActionGroup() {
        application.assertIsDispatchThread()

        popupActionGroup.removeAll()
        for (runnableProject in availableProjects.value) {
            popupActionGroup.add(object : DumbAwareAction(
                { runnableProject.name },
                calculateIcon(runnableProject)
            ) {
                override fun actionPerformed(event: AnActionEvent) {
                    selectedRunnableProjectProperty.set(runnableProject)
                }
            })
        }
    }

    private fun autoSelectProject() {
        application.assertIsDispatchThread()

        val availableProjects = availableProjects.value
        val nothingSelected = !selectedRunnableProjectProperty.hasValue
        val shouldSynchronize = avaloniaProjectSettings.synchronizeWithRunConfiguration

        fun getRunnableProject(projectFilePath: Path?): RunnableProject? =
            availableProjects.firstOrNull {
                FileUtil.pathsEqual(it.projectFilePath, projectFilePath.toString())
            }

        fun getTargetProject(): RunnableProject {
            logger.info("Determining target project path")
            if (shouldSynchronize) {
                val currentConfiguration = runManager.selectedConfiguration?.configuration as? IProjectBasedRunConfiguration
                if (currentConfiguration != null) {
                    logger.info("Trying to synchronize with run configuration $currentConfiguration")
                    val currentProjectFilePath = Paths.get(currentConfiguration.getProjectFilePath())
                    val currentProject = getRunnableProject(currentProjectFilePath)
                    if (currentProject != null) {
                        logger.info("Synchronization success: $currentProjectFilePath")
                        return currentProject
                    } else {
                        logger.info("Synchronization failed: $currentProjectFilePath")
                    }
                }
            }

            logger.info("Trying to load saved project")
            val savedProjectFilePath =  avaloniaWorkspaceSettings.getSelection(xamlFile.toNioPath())
            if (savedProjectFilePath != null) {
                logger.info("Saved project file path: $savedProjectFilePath")
                val savedProject = getRunnableProject(savedProjectFilePath)
                if (savedProject != null) {
                    logger.info("Synchronization with saved project file: success")
                    return savedProject
                } else {
                    logger.warn("Could not found project \"$savedProjectFilePath\" saved for XAML file \"$xamlFile\" among ${availableProjects.size} available runnable projects")
                }
            }

            logger.info("Returning first available project")
            return availableProjects.first()
        }

        if (availableProjects.isNotEmpty() && (nothingSelected || shouldSynchronize)) {
            val targetProject = getTargetProject()
            selectedRunnableProjectProperty.set(targetProject)
        }
    }

    init {
        runnableProjects.view(lifetime, ::fillWithActions)
        availableProjects.advise(lifetime) {
            updateActionGroup()
            autoSelectProject()
        }
        selectedRunnableProjectProperty.advise(lifetime) { project ->
            avaloniaWorkspaceSettings.storeSelection(xamlFile.toNioPath(), Paths.get(project.projectFilePath))
        }
        messageBus.connect(lifetime.createNestedDisposable("RunnableAssemblySelectorAction"))
            .subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
                override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
                    autoSelectProject()
                }

                override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
                    autoSelectProject()
                }
            })
    }

    private fun calculateIcon(runnableProject: RunnableProject) =
        VfsUtil.findFile(Paths.get(runnableProject.projectFilePath), false)?.let { virtualFile ->
            workspaceModel.getProjectModelEntities(virtualFile, project).singleOrNull {
                it.isProject() || it.isUnloadedProject()
            }
        }?.calculateIcon(project)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val selectedProject = selectedRunnableProjectProperty.valueOrNull
        val isLoading = isLoading.value
        e.presentation.apply {
            when {
                isLoading -> {
                    isEnabled = false
                    icon = null
                    text = message("assemblySelector.loading")
                    description = null
                }
                selectedProject == null -> {
                    isEnabled = true
                    icon = AllIcons.General.Warning
                    @Suppress("DialogTitleCapitalization")
                    text = message("assemblySelector.noProject")
                    description = message("assemblySelector.noProjectDescription")
                }
                else -> {
                    isEnabled = true
                    icon = calculateIcon(selectedProject)
                    text = selectedProject.name
                    description = null
                }
            }
        }
    }

    private fun fillWithActions(valueLifetime: Lifetime, filteredProjects: Sequence<RunnableProject>) {
        isProcessingProjectList.set(true)
        valueLifetime.launch(Dispatchers.EDT) {
            val selectableRunnableProjects = getSelectableRunnableProjects(filteredProjects)
            valueLifetime.executeIfAlive {
                availableProjects.set(selectableRunnableProjects)
                isProcessingProjectList.set(false)
            }
        }
    }

    private suspend fun getSelectableRunnableProjects(
        filteredProjects: Sequence<RunnableProject>
    ): List<RunnableProject> {
        val filteredProjectList = filteredProjects.toList()
        val targetFileProjectEntity = xamlFile.getProjectContainingFile(lifetime, project)
        val targetFileProjectPath = targetFileProjectEntity.url!!.toPath()
        val runnableProjectPaths = filteredProjectList
            .filter { !FileUtil.pathsEqual(it.projectFilePath, targetFileProjectPath.toString()) }
            .map { it.projectFilePath }
        val availableProjectPaths = mutableListOf<String>()
        if (runnableProjectPaths.isNotEmpty()) {
            val refRequest = RdGetReferencingProjectsRequest(targetFileProjectPath.toString(), runnableProjectPaths)

            logger.info("Calculating referencing projects for project ${targetFileProjectPath.nameWithoutExtension} among ${runnableProjectPaths.size} runnable projects")
            val actuallyReferencedProjects = avaloniaRiderModel.getReferencingProjects.startSuspending(
                lifetime,
                refRequest
            )
            logger.info("There are ${actuallyReferencedProjects.size} projects referencing ${targetFileProjectPath.nameWithoutExtension} among the passed ones")

            availableProjectPaths.addAll(actuallyReferencedProjects)
        }

        if (filteredProjectList.any {
                FileUtil.pathsEqual(it.projectFilePath, targetFileProjectPath.toString())
            }) {
            logger.info("Target project path \"${targetFileProjectPath}\" is also available for selection")
            availableProjectPaths.add(targetFileProjectPath.toString())
        }

        val runnableProjectPerPath =
            filteredProjectList.associateBy { r ->
                Paths.get(r.projectFilePath).toRealPath().systemIndependentPath
            }

        val selectableRunnableProjects = availableProjectPaths.mapNotNull { path ->
            val normalizedPath = Paths.get(path).toRealPath().systemIndependentPath
            val runnableProject = runnableProjectPerPath[normalizedPath]
            if (runnableProject == null) {
                logger.error("Couldn't find runnable project for path $normalizedPath")
            }
            runnableProject
        }.sortedBy { it.name }
        return selectableRunnableProjects
    }
}
