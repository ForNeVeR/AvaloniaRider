package me.fornever.avaloniarider.previewer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.*
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createNestedDisposable
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import com.jetbrains.rider.util.idea.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fornever.avaloniarider.idea.AvaloniaToolWindowManager
import me.fornever.avaloniarider.idea.concurrency.ApplicationAnyModality
import org.jetbrains.annotations.NotNull
import java.nio.file.Path

data class AvaloniaPreviewerParameters(
    val runtime: DotNetRuntime,
    val previewerBinary: Path,
    val targetDir: Path,
    val targetName: String,
    val targetPath: Path
)

object AvaloniaPreviewerProcess {
    fun getCommandLine(
        parameters: AvaloniaPreviewerParameters,
        bsonPort: Int
    ): GeneralCommandLine {
        val runtimeConfig = parameters.targetDir.resolve("${parameters.targetName}.runtimeconfig.json")
        val depsFile = parameters.targetDir.resolve("${parameters.targetName}.deps.json")
        return when (parameters.runtime) {
            is DotNetCoreRuntime -> GeneralCommandLine().withExePath(parameters.runtime.cliExePath)
                .withParameters(
                    "exec",
                    "--runtimeconfig",
                    runtimeConfig.toAbsolutePath().toString(),
                    "--depsfile",
                    depsFile.toAbsolutePath().toString(),
                    parameters.previewerBinary.toAbsolutePath().toString(),
                    "--transport",
                    "tcp-bson://127.0.0.1:$bsonPort/",
                    parameters.targetPath.toAbsolutePath().toString()
                )
            else -> GeneralCommandLine().withExePath(parameters.previewerBinary.toAbsolutePath().toString())
                .withParameters(
                    "--transport",
                    "tcp-bson://127.0.0.1:$bsonPort/",
                    parameters.targetPath.toAbsolutePath().toString()
                )
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

    private fun startProcess(lifetime: Lifetime, commandLine: GeneralCommandLine, consoleView: ConsoleView): OSProcessHandler {
        val processHandler = object : OSProcessHandler(commandLine) {
            override fun readerOptions() =
                BaseOutputReader.Options.forMostlySilentProcess()
        }

        processHandler.startNotify()
        lifetime.onTermination { processHandler.destroyProcess() }

        consoleView.attachToProcess(processHandler)
        return processHandler
    }

    private suspend fun waitForTermination(lifetime: Lifetime, process: ProcessHandler) {
        val result = CompletableDeferred<Unit>()
        process.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent) {
                result.complete(Unit)
            }
        }, lifetime.createNestedDisposable("AvaloniaPreviewerProcess::waitForTermination"))
        result.await()
    }

    suspend fun run(project: Project, lifetime: Lifetime, commandLine: GeneralCommandLine) {
        val consoleView = withContext(Dispatchers.ApplicationAnyModality) { registerNewConsoleView(project) }
        val process = startProcess(lifetime, commandLine, consoleView)
        waitForTermination(lifetime, process)
    }
}
