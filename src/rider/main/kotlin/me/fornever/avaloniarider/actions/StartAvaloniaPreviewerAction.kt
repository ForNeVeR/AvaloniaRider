package me.fornever.avaloniarider.actions

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.util.Logger
import com.jetbrains.rider.util.error
import com.jetbrains.rider.util.getLogger
import com.jetbrains.rider.util.info
import com.jetbrains.rider.util.string.printToString
import java.net.ServerSocket
import java.nio.file.Path
import java.nio.file.Paths

private fun getDesignerCommandLine(
        runtime: DotNetCoreRuntime,
        designerBinary: Path,
        targetDir: Path,
        targetName: String,
        targetPath: Path,
        bsonPort: Int
): GeneralCommandLine {
    val runtimeConfig = targetDir.resolve("$targetName.runtimeconfig.json")
    val depsFile = targetDir.resolve("$targetName.deps.json")
    return GeneralCommandLine().withExePath(runtime.cliExePath)
            .withParameters(
                    "exec",
                    "--runtimeconfig",
                    runtimeConfig.toAbsolutePath().toString(),
                    "--depsfile",
                    depsFile.toAbsolutePath().toString(),
                    designerBinary.toAbsolutePath().toString(),
                    "--transport",
                    "tcp-bson://127.0.0.1:$bsonPort/",
                    targetPath.toAbsolutePath().toString())
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
    } catch(ex: Throwable) {
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
        msBuildEvaluator.evaluateProperties(
                runnableProject.projectFilePath,
                listOf("NuGetPackageRoot", "TargetDir", "TargetName", "TargetPath"))
                .then { properties ->
                    val nuGetPackageRoot = Paths.get(properties["NuGetPackageRoot"])
                    val targetDir = Paths.get(properties["TargetDir"])
                    val targetName = properties["TargetName"]!!
                    val targetPath = Paths.get(properties["TargetPath"])

                    // TODO[von Never]: properly determine Avalonia version for the project
                    val avaloniaVersion = "0.7.0"
                    val avaloniaPackageDir = nuGetPackageRoot.resolve("avalonia").resolve(avaloniaVersion)
                    val avaloniaDesignerBinary = avaloniaPackageDir.resolve(
                            "tools/netcoreapp2.0/designer/Avalonia.Designer.HostApp.dll")
                    // TODO[von Never]: properly determine whether we use .NET Core or not

                    val runtimeHost = RiderDotNetActiveRuntimeHost.getInstance(project)
                    val runtime = runtimeHost.dotNetCoreRuntime
                            ?: throw CantRunException("Cannot determine .NET Core runtime")

                    val serverSocket = startListening() // TODO[von Never]: close socket on error or anything
                    val commandLine = getDesignerCommandLine(
                            runtime,
                            avaloniaDesignerBinary,
                            targetDir,
                            targetName,
                            targetPath,
                            serverSocket.localPort)
                    startListeningTask(logger, serverSocket)
                    startAndShowOutput(project, commandLine)
                }.onError { logger.error(it) }
    }
}
