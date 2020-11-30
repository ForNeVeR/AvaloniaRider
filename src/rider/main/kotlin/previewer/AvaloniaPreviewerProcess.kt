package me.fornever.avaloniarider.previewer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.rd.framework.util.NetUtils
import com.jetbrains.rd.platform.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fornever.avaloniarider.idea.AvaloniaToolWindowManager
import me.fornever.avaloniarider.idea.concurrency.ApplicationAnyModality
import java.nio.file.Path

data class AvaloniaPreviewerParameters(
    val runtime: DotNetRuntime,
    val previewerBinary: Path,
    val targetDir: Path,
    val targetName: String,
    val targetPath: Path
)

class AvaloniaPreviewerProcess(
    private val lifetime: Lifetime,
    private val parameters: AvaloniaPreviewerParameters
) {
    companion object {
        private val logger = Logger.getInstance(AvaloniaPreviewerProcess::class.java)
    }

    private fun getCommandLine(transport: PreviewerTransport, method: PreviewerMethod): GeneralCommandLine {
        val runtimeConfig = parameters.targetDir.resolve("${parameters.targetName}.runtimeconfig.json")
        val depsFile = parameters.targetDir.resolve("${parameters.targetName}.deps.json")
        val previewerArguments =
            transport.getOptions() +
            method.getOptions { NetUtils.findFreePort(5000) } +
            parameters.targetPath.toAbsolutePath().toString()
        return when (parameters.runtime) {
            is DotNetCoreRuntime -> GeneralCommandLine().withExePath(parameters.runtime.cliExePath)
                .withParameters(
                    "exec",
                    "--runtimeconfig", runtimeConfig.toAbsolutePath().toString(),
                    "--depsfile", depsFile.toAbsolutePath().toString(),
                    parameters.previewerBinary.toAbsolutePath().toString()
                ).withParameters(previewerArguments)
            else -> GeneralCommandLine().withExePath(parameters.previewerBinary.toAbsolutePath().toString())
                .withParameters(previewerArguments)
        }
    }

    private fun registerNewConsoleView(project: Project): ConsoleView {
        application.assertIsDispatchThread()

        val toolWindowManager = AvaloniaToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.toolWindow.value
        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val content = toolWindow.contentManager.factory.createContent(consoleView.component, "Avalonia Designer", true)
        toolWindow.contentManager.addContent(content)

        return consoleView
    }

    private fun startProcess(commandLine: GeneralCommandLine, consoleView: ConsoleView): OSProcessHandler {
        val processHandler = object : OSProcessHandler(commandLine) {
            override fun readerOptions() =
                BaseOutputReader.Options.forMostlySilentProcess()

            override fun notifyTextAvailable(text: String, outputType: Key<*>) {
                if (application.isUnitTestMode)
                    logger.info("$outputType: ${text}")
                super.notifyTextAvailable(text, outputType)
            }
        }

        logger.info("Starting process ${commandLine.commandLineString}")
        processHandler.startNotify()
        lifetime.onTermination { processHandler.destroyProcess() }

        consoleView.attachToProcess(processHandler)
        return processHandler
    }

    private suspend fun waitForTermination(process: ProcessHandler) {
        val result = CompletableDeferred<Unit>()
        process.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                result.complete(Unit)
            }
        }, lifetime.createNestedDisposable("AvaloniaPreviewerProcess::waitForTermination"))
        result.await()
    }

    suspend fun run(project: Project, transport: PreviewerTransport, method: PreviewerMethod) {
        val commandLine = getCommandLine(transport, method)
        val consoleView = withContext(Dispatchers.ApplicationAnyModality) { registerNewConsoleView(project) }
        val process = startProcess(commandLine, consoleView)
        waitForTermination(process)
    }
}
