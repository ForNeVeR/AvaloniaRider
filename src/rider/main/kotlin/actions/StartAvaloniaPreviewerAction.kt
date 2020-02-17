package me.fornever.avaloniarider.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.util.idea.lifetime
import me.fornever.avaloniarider.controlmessages.AvaloniaMessages
import me.fornever.avaloniarider.idea.AvaloniaRiderNotifications
import me.fornever.avaloniarider.previewer.AvaloniaPreviewer
import me.fornever.avaloniarider.previewer.AvaloniaPreviewerSession
import java.net.ServerSocket
import java.nio.file.Paths

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

// TODO[F]: Delete this class
class StartAvaloniaPreviewerAction : AnAction("Start Avalonia Previewer") {
    companion object {
        private val logger = getLogger<StartAvaloniaPreviewerAction>()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.solution?.isLoaded?.valueOrNull == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val runnableProject = project.solution.runnableProjectsModel.projects.valueOrNull?.firstOrNull() ?: return
        val currentDocument = e.getRequiredData(CommonDataKeys.EDITOR).document
        val currentFile = FileDocumentManager.getInstance().getFile(currentDocument) ?: return

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
            val serverSocket = ServerSocket(0)
            try {
                val commandLine = AvaloniaPreviewer.getPreviewerCommandLine(
                    runtime,
                    previewerPath,
                    targetDir,
                    targetName,
                    targetPath,
                    serverSocket.localPort)

                logger.info { "previewerPath $previewerPath" }
                logger.info { "targetDir $targetDir" }
                logger.info { "targetName $targetName" }
                logger.info { "targetPath $targetPath" }

                val session = AvaloniaPreviewerSession(
                    project.lifetime,
                    AvaloniaMessages.getInstance(),
                    serverSocket,
                    targetPath,
                    currentFile
                )
                session.start()
                AvaloniaPreviewer.startDesignerProcess(project, commandLine)
            } catch (t: Throwable) {
                serverSocket.close()
                throw t
            }
        }.onError { logger.error(it) }
    }
}
