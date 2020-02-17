package me.fornever.avaloniarider.previewer

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.openapi.project.Project
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import me.fornever.avaloniarider.idea.AvaloniaToolWindowManager
import java.nio.file.Path

object AvaloniaPreviewer {
    fun getPreviewerCommandLine(
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

    fun getAvaloniaPreviewerPathKey(runtime: DotNetRuntime): String = when (runtime) {
        is DotNetCoreRuntime -> "AvaloniaPreviewerNetCoreToolPath"
        else -> "AvaloniaPreviewerNetFullToolPath"
    }

    // TODO[F]: Pass a lifetime here
    fun startDesignerProcess(project: Project, commandLine: GeneralCommandLine) {
        val processHandlerFactory = ProcessHandlerFactory.getInstance()
        val processHandler = processHandlerFactory.createProcessHandler(commandLine)

        val toolWindowManager = AvaloniaToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.toolWindow.value
        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val content = toolWindow.contentManager.factory.createContent(consoleView.component, "Avalonia Designer", true)
        toolWindow.contentManager.addContent(content)

        processHandler.startNotify()
        consoleView.attachToProcess(processHandler)
    }
}
