package me.fornever.avaloniarider.rider

import com.google.common.collect.Queues
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.adviseUntil
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.model.riderSolutionLifecycle
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.containingProjectEntity
import com.jetbrains.rider.projectView.workspace.getProjectModelEntities
import kotlinx.coroutines.CompletableDeferred

val ProjectModelEntity.projectRelativeVirtualPath: String
    get() {
        val names = Queues.newArrayDeque<String>()
        var current: ProjectModelEntity? = this
        while (current != null && current.descriptor !is RdProjectDescriptor) {
            names.push(current.name)
            current = current.parentEntity
        }
        return names.joinToString("/")
    }

suspend fun VirtualFile.getProjectContainingFile(lifetime: Lifetime, project: Project): ProjectModelEntity? {
    val logger = Logger.getInstance("me.fornever.avaloniarider.rider.ProjectModelNodeExKt")
    val workspaceModel = WorkspaceModel.getInstance(project)

    application.assertIsDispatchThread()

    val result = CompletableDeferred<ProjectModelEntity?>()

    project.solution.riderSolutionLifecycle.isProjectModelReady.adviseUntil(lifetime) { isReady ->
        if (!isReady) return@adviseUntil false
        try {
            logger.debug { "Project model view synchronized" }
            val projectModelEntities = workspaceModel.getProjectModelEntities(this, project)
            logger.debug {
                "Project model nodes for file $this: " + projectModelEntities.joinToString(", ")
            }
            val containingProject = projectModelEntities.asSequence()
                .mapNotNull { it.containingProjectEntity() }
                .firstOrNull()
            if (containingProject != null) {
                result.complete(containingProject)
            } else {
                logger.warn("Workspace model doesn't contain project entity for file $this")
                result.complete(null)
            }
        } catch (t: Throwable) {
            result.completeExceptionally(t)
        }

        return@adviseUntil true
    }

    return result.await()
}
