package me.fornever.avaloniarider.previewer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.openapi.util.Key
import com.intellij.util.application
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.rd.framework.util.NetUtils
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.CompletableDeferred
import me.fornever.avaloniarider.idea.AvaloniaToolWindowManager
import java.nio.file.Path

data class AvaloniaPreviewerParameters(
    val runtime: DotNetRuntime,
    val previewerBinary: Path,
    val targetDir: Path,
    val targetName: String,
    /**
     * Path to the executable assembly.
     */
    val targetPath: Path,
    /**
     * Path to the assembly containing a XAML file in question.
     */
    val xamlContainingAssemblyPath: Path
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

    private fun startProcess(
        lifetime: Lifetime,
        project: Project,
        commandLine: GeneralCommandLine,
        consoleView: ConsoleView,
        title: String
    ): OSProcessHandler {
        val processHandler = object : OSProcessHandler(commandLine) {
            override fun readerOptions() =
                BaseOutputReader.Options.forMostlySilentProcess()

            override fun notifyTextAvailable(text: String, outputType: Key<*>) {
                logger.info("$title [$outputType]: $text")
                super.notifyTextAvailable(text, outputType)
            }

            override fun notifyProcessTerminated(exitCode: Int) {
                consoleView.print("Process terminated with exit code $exitCode", ConsoleViewContentType.SYSTEM_OUTPUT)
                logger.info("Process $title exited with $exitCode")
                super.notifyProcessTerminated(exitCode)
            }
        }

        consoleView.attachToProcess(processHandler)

        logger.info("Starting process ${commandLine.commandLineString}")
        processHandler.startNotify()
        ProcessReaper.getInstance(project).registerProcess(lifetime, processHandler)

        return processHandler
    }

    private suspend fun waitForTermination(process: ProcessHandler, title: String) {
        val result = CompletableDeferred<Unit>()
        process.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                result.complete(Unit)
            }
        }, lifetime.createNestedDisposable("AvaloniaPreviewerProcess::waitForTermination"))
        if (process.isProcessTerminated) {
            logger.warn("Already terminated: $title")
            return
        }
        result.await()
    }

    suspend fun run(
        lifetime: Lifetime,
        project: Project,
        transport: PreviewerTransport,
        method: PreviewerMethod,
        title: String
    ) {
        logger.info("1/4: generating process command line")
        val commandLine = getCommandLine(transport, method)
        logger.info("2/4: creating a console view")
        val consoleView = withUiContext { registerNewConsoleView(project) }
        logger.info("3/4: starting a process")
        val process = startProcess(lifetime, project, commandLine, consoleView, title)
        logger.info("4/4: awaiting termination")
        waitForTermination(process, title)
    }
}
