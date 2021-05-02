package me.fornever.avaloniarider.idea.editor.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IOptProperty
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.valueOrDefault
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent

class RunnableAssemblySelectorAction(
    lifetime: Lifetime,
    isSolutionLoading: IOptProperty<Boolean>,
    runnableProjects: IOptProperty<List<RunnableProject>>
) : ComboBoxAction() {
    constructor(lifetime: Lifetime, project: Project) : this(
        lifetime,
        project.solution.isLoading,
        project.solution.runnableProjectsModel.projects
    )

    init {
        runnableProjects.advise(lifetime, ::fillWithActions)
    }

    // TODO: Initial assembly selection
    // TODO: Filter by only referenced assemblies
    // TODO: Persist user selection; base initial assembly guess on already persisted files from the current assembly
    val group: DefaultActionGroup = object : DefaultActionGroup() {
        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.isEnabled = !isSolutionLoading.valueOrDefault(false)
        }
    }
    override fun createPopupActionGroup(button: JComponent?) = group

    private val selectedProjectPathProperty = Property<Path?>(null)
    val selectedProjectPath: IPropertyView<Path?> = selectedProjectPathProperty

    private fun fillWithActions(runnableProjects: List<RunnableProject>) {
        group.removeAll()
        for (runnableProject in runnableProjects) {
            group.add(object : AnAction(runnableProject.name) {
                override fun actionPerformed(event: AnActionEvent) {
                    selectedProjectPathProperty.set(Paths.get(runnableProject.projectFilePath))
                }
            })
        }
    }

}
