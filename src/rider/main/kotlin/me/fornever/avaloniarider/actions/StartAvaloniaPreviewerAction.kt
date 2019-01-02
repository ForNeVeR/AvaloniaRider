package me.fornever.avaloniarider.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.DotNetExecutable
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.util.Logger
import com.jetbrains.rider.util.error
import com.jetbrains.rider.util.getLogger
import com.jetbrains.rider.util.info
import com.jetbrains.rider.util.string.printToString
import me.fornever.avaloniarider.AvaloniaRiderNotifications
import java.net.ServerSocket
import java.nio.file.Path
import java.nio.file.Paths

private fun getRuntime(
        runtimeHost: RiderDotNetActiveRuntimeHost,
        runnableProject: RunnableProject): DotNetRuntime? {
    val output = runnableProject.projectOutputs.firstOrNull() ?: return null
    val executable = DotNetExecutable(
            output.exePath,
            output.tfm,
            "",
            emptyList(),
            false,
            false,
            emptyMap(),
            false,
            { _, _ -> },
            null,
            "",
            false)
    return DotNetRuntime.getSuitableRuntime(runnableProject.kind, runtimeHost, executable)
}

private fun getAvaloniaPreviewerPathKey(runtime: DotNetRuntime): String = when (runtime) {
    is DotNetCoreRuntime -> "AvaloniaPreviewerNetCoreToolPath"
    else -> "AvaloniaPreviewerNetFullToolPath"
}

private fun getDesignerCommandLine(
        runtime: DotNetRuntime,
        previewerBinary: Path,
        targetDir: Path,
        targetName: String,
        targetPath: Path,
        bsonPort: Int
): GeneralCommandLine {
    val runtimeConfig = targetDir.resolve("$targetName.runtimeconfig.json")
    val depsFile = targetDir.resolve("$targetName.deps.json")
    return when (runtime) {
        is DotNetCoreRuntime -> GeneralCommandLine().withExePath(runtime.cliExePath)
                .withParameters(
                        "exec",
                        "--runtimeconfig",
                        runtimeConfig.toAbsolutePath().toString(),
                        "--depsfile",
                        depsFile.toAbsolutePath().toString(),
                        previewerBinary.toAbsolutePath().toString(),
                        "--transport",
                        "tcp-bson://127.0.0.1:$bsonPort/",
                        targetPath.toAbsolutePath().toString()
                )
        else -> GeneralCommandLine().withExePath(previewerBinary.toAbsolutePath().toString())
                .withParameters(
                        "--transport",
                        "tcp-bson://127.0.0.1:$bsonPort/",
                        targetPath.toAbsolutePath().toString()
                )
    }
}

private fun startListening() = ServerSocket(0)

private fun startListeningTask(logger: Logger, serverSocket: ServerSocket) = Thread {
    try {
        val socket = serverSocket.accept()
        socket.getInputStream().use { input ->
            while (!socket.isClosed) {
                val buffer = ByteArray(128)
                val size = input.read(buffer)
                logger.info { "Data received: " + buffer.printToString() }
                if (size == -1) return@Thread
            }
        }
    } catch (ex: Throwable) {
        logger.error("Error while listening to Avalonia designer socket", ex)
    }
}.apply { start() }

private var toolWindow: ToolWindow? = null

private fun initToolWindow(project: Project): ToolWindow {
    if (toolWindow == null) {
        toolWindow = ToolWindowManager.getInstance(project).registerToolWindow("Commands", true, ToolWindowAnchor.BOTTOM)
    }

    return toolWindow!!
}

private fun startAndShowOutput(project: Project, commandLine: GeneralCommandLine) {
    val processHandlerFactory = ProcessHandlerFactory.getInstance()
    val processHandler = processHandlerFactory.createProcessHandler(commandLine)

    val toolWindow = initToolWindow(project)
    val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    val content = toolWindow.contentManager.factory.createContent(consoleView.component, "Avalonia Designer", true)
    toolWindow.contentManager.addContent(content)

    processHandler.startNotify()
    consoleView.attachToProcess(processHandler)
}

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
        val msBuildEvaluator = MSBuildEvaluator.getInstance(project)
        val runtime = getRuntime(RiderDotNetActiveRuntimeHost.getInstance(project), runnableProject) ?: return
        val avaloniaPreviewerPathKey = getAvaloniaPreviewerPathKey(runtime)
        msBuildEvaluator.evaluateProperties(
                runnableProject.projectFilePath,
                listOf(avaloniaPreviewerPathKey, "TargetDir", "TargetName", "TargetPath")
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
            val targetDir = Paths.get(properties["TargetDir"])
            val targetName = properties["TargetName"]!!
            val targetPath = Paths.get(properties["TargetPath"])

            val serverSocket = startListening() // TODO[von Never]: close socket on error or anything
            val commandLine = getDesignerCommandLine(
                    runtime,
                    previewerPath,
                    targetDir,
                    targetName,
                    targetPath,
                    serverSocket.localPort)
            startListeningTask(logger, serverSocket)
            startAndShowOutput(project, commandLine)
        }.onError { logger.error(it) }
    }
}
