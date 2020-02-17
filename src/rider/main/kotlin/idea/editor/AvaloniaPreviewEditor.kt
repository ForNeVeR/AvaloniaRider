package me.fornever.avaloniarider.idea.editor

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.valueOrThrow
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import me.fornever.avaloniarider.actions.StartAvaloniaPreviewerAction
import me.fornever.avaloniarider.controlmessages.AvaloniaMessages
import me.fornever.avaloniarider.idea.AvaloniaRiderNotifications
import me.fornever.avaloniarider.previewer.AvaloniaPreviewer
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSession
import java.beans.PropertyChangeListener
import java.net.ServerSocket
import java.nio.file.Paths

class AvaloniaPreviewEditor(
    private val project: Project,
    private val currentFile: VirtualFile
) : UserDataHolderBase(), FileEditor {

    companion object {
        private val logger = getLogger<StartAvaloniaPreviewerAction>()
    }

    override fun getName() = "Previewer"
    override fun isValid() = true

    override fun getFile() = currentFile

    private val lifetime = LifetimeDefinition()
    private val panel = lazy {
        val socket = ServerSocket(0) // TODO[F]: Open in an asynchronous way
        val assemblyPath = project.solution.runnableProjectsModel.projects
            .valueOrThrow.first()
            .projectOutputs.first().exePath // TODO[F]: Properly calculate the assembly path asynchronously
        val session = AvaloniaPreviewerSession(
            lifetime,
            AvaloniaMessages.getInstance(),
            socket,
            Paths.get(assemblyPath),
            currentFile
        ).apply { this.start() }
        startPreviewerProcess(socket)
        AvaloniaPreviewEditorComponent(lifetime, session)
    }

    override fun getComponent() = panel.value
    override fun getPreferredFocusedComponent() = component

    override fun isModified() = false
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun setState(state: FileEditorState) {}
    override fun selectNotify() {}
    override fun deselectNotify() {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
    override fun dispose() {
        lifetime.terminate()
    }

    private fun getRuntime(
        runtimeHost: RiderDotNetActiveRuntimeHost,
        runnableProject: RunnableProject): DotNetRuntime? {
        val output = runnableProject.projectOutputs.firstOrNull() ?: return null
        return DotNetRuntime.detectRuntimeForProjectOrThrow(
            runnableProject.kind,
            runtimeHost,
            false,
            output.exePath,
            output.tfm
        )
    }

    // TODO[F]: This should be moved to an appropriate place
    private fun startPreviewerProcess(socket: ServerSocket) {
        val runnableProject = project.solution.runnableProjectsModel.projects.valueOrNull?.firstOrNull() ?: return

        val msBuildEvaluator = MSBuildEvaluator.getInstance(project)
        val runtime = getRuntime(RiderDotNetActiveRuntimeHost.getInstance(project), runnableProject) ?: return
        val avaloniaPreviewerPathKey = AvaloniaPreviewer.getAvaloniaPreviewerPathKey(runtime)
        msBuildEvaluator.evaluateProperties(
            MSBuildEvaluator.PropertyRequest(
                runnableProject.projectFilePath,
                null,
                listOf(avaloniaPreviewerPathKey, "TargetDir", "TargetName", "TargetPath")
            )
        ).then { properties ->
            val previewerPathValue = properties[avaloniaPreviewerPathKey]
            if (previewerPathValue.isNullOrEmpty()) {
                val notifications = AvaloniaRiderNotifications.getInstance()
                notifications.showNotification(
                    "Avalonia could not be found. Please ensure project ${runnableProject.name} includes package Avalonia version 0.7 or higher"
                )
                return@then
            }

            val previewerPath = Paths.get(previewerPathValue)
            val targetDir = Paths.get(properties.getValue("TargetDir"))
            val targetName = properties.getValue("TargetName")
            val targetPath = Paths.get(properties.getValue("TargetPath"))

            // TODO[F]: Move socket management into the Avalonia previewer session
            try {
                val commandLine = AvaloniaPreviewer.getPreviewerCommandLine(
                    runtime,
                    previewerPath,
                    targetDir,
                    targetName,
                    targetPath,
                    socket.localPort)

                logger.info { "previewerPath $previewerPath" }
                logger.info { "targetDir $targetDir" }
                logger.info { "targetName $targetName" }
                logger.info { "targetPath $targetPath" }

                AvaloniaPreviewer.startDesignerProcess(project, commandLine)
            } catch (t: Throwable) {
                socket.close()
                throw t
            }
        }.onError { logger.error(it) }
    }
}
