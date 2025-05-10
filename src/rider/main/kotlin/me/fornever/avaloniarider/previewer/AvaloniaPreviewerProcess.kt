package me.fornever.avaloniarider.previewer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.rd.framework.util.NetUtils
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import kotlinx.coroutines.CompletableDeferred
import me.fornever.avaloniarider.AvaloniaRiderBundle
import me.fornever.avaloniarider.rider.createExeConfiguration
import me.fornever.avaloniarider.rider.launchDebugger
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
     * Path to the assembly containing the XAML file in question.
     */
    val xamlContainingAssemblyPath: Path,
    val workingDirectory: Path
)

class AvaloniaPreviewerProcess(
    private val project: Project,
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
        val runtime = parameters.runtime
        val runtimeArguments =
            if (runtime is DotNetCoreRuntime) {
                listOf(
                    "exec",
                    "--runtimeconfig", runtimeConfig.toAbsolutePath().toString(),
                    "--depsfile", depsFile.toAbsolutePath().toString()
                )
            } else emptyList()

        logger.info("Previewer process path: \"${parameters.previewerBinary}\".")
        return GeneralCommandLine()
            .withExePath(parameters.previewerBinary.toAbsolutePath().toString())
            .withParameters(previewerArguments)
            .withWorkDirectory(parameters.workingDirectory.toFile())
            .apply {
                runtime.patchRunCommandLine(this, runtimeArguments)
            }
    }

    private suspend fun startProcess(
        executionMode: ProcessExecutionMode,
        commandLine: GeneralCommandLine,
        consoleView: ConsoleView?,
        title: String
    ): ProcessHandler = when (executionMode) {
        ProcessExecutionMode.Run -> runProcess(commandLine, consoleView, title)
        ProcessExecutionMode.Debug -> debugProcess(commandLine, consoleView)
    }

    private fun runProcess(
        commandLine: GeneralCommandLine,
        consoleView: ConsoleView?,
        title: String
    ): OSProcessHandler {
        val processHandler = lifetime.bracketOrThrowEx({
            object : OSProcessHandler(commandLine) {
                override fun readerOptions() =
                    BaseOutputReader.Options.forMostlySilentProcess()

                override fun notifyTextAvailable(text: String, outputType: Key<*>) {
                    logger.info("$title [$outputType]: $text")
                    super.notifyTextAvailable(text, outputType)
                }

                override fun notifyProcessTerminated(exitCode: Int) {
                    consoleView?.print(
                        AvaloniaRiderBundle.message("previewer.console.process-terminated", exitCode) + "\n",
                        ConsoleViewContentType.SYSTEM_OUTPUT
                    )
                    logger.info("Process $title exited with $exitCode")
                    super.notifyProcessTerminated(exitCode)
                }
            }
        }) { handler ->
            handler.destroyProcess()
            handler.waitFor()
        }

        consoleView?.attachToProcess(processHandler)

        logger.info("Starting process ${commandLine.commandLineString}")
        processHandler.startNotify()

        return processHandler
    }

    private suspend fun debugProcess(commandLine: GeneralCommandLine, consoleView: ConsoleView?): ProcessHandler {
        consoleView?.print(
            AvaloniaRiderBundle.message("previewer.console.debugging-started"),
            ConsoleViewContentType.SYSTEM_OUTPUT
        )
        val configuration = createExeConfiguration(project, commandLine)
        val contentDescriptor = launchDebugger(lifetime, configuration)
        return contentDescriptor.processHandler ?: error("Process handler is not available")
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
        executionMode: ProcessExecutionMode,
        consoleView: ConsoleView?,
        transport: PreviewerTransport,
        method: PreviewerMethod,
        title: String
    ) {
        logger.info("1/4: generating process command line")
        val commandLine = getCommandLine(transport, method)
        logger.info("2/3: starting a process")
        val process = startProcess(executionMode, commandLine, consoleView, title)
        logger.info("3/3: awaiting termination")
        waitForTermination(process, title)
    }
}

enum class ProcessExecutionMode {
    Run,
    Debug
}
