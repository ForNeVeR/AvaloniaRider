package me.fornever.avaloniarider.rider

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rider.run.configurations.dotNetExe.DotNetExeConfiguration
import com.jetbrains.rider.run.configurations.dotNetExe.DotNetExeConfigurationParameters
import com.jetbrains.rider.run.configurations.dotNetExe.DotNetExeConfigurationType
import com.jetbrains.rider.run.pid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun createExeConfiguration(project: Project, commandLine: GeneralCommandLine): DotNetExeConfiguration {
    val factory = ConfigurationTypeUtil.findConfigurationType(DotNetExeConfigurationType::class.java).factory
    return DotNetExeConfiguration(
        ".NET Executable",
        project,
        factory,
        DotNetExeConfigurationParameters(
            project = project,
            exePath = commandLine.exePath,
            programParameters = ParametersListUtil.join(commandLine.parametersList.parameters),
            workingDirectory = commandLine.workDirectory?.path ?: "",
            envs = commandLine.environment,
            isPassParentEnvs = true,
            useExternalConsole = false,
            executeAsIs = true,
            assemblyToDebug = null,
            runtimeArguments = ""
        )
    )
}

suspend fun launchConfiguration(lifetime: Lifetime, configuration: DotNetExeConfiguration): RunContentDescriptor {
    val environment = ExecutionEnvironmentBuilder.create(
        DefaultDebugExecutor.getDebugExecutorInstance(),
        configuration
    ).build()

    val contentDescriptor = withContext(Dispatchers.EDT) {
        suspendCoroutine<RunContentDescriptor?> { cont ->
            logger.info("Starting configuration: ${configuration.name}.")
            ProgramRunnerUtil.executeConfigurationAsync(environment, false, true, object : ProgramRunner.Callback {
                override fun processStarted(descriptor: RunContentDescriptor?) {
                    logger.info("Configuration started: ${configuration.name}.")
                    cont.resume(descriptor)
                }

                override fun processNotStarted(error: Throwable?) {
                    logger.info("Configuration not started: ${configuration.name}.")
                    cont.resumeWithException(error ?: RuntimeException("Unknown execution error"))
                }
            })
        }
    }

    lifetime.onTermination {
        val processHandler = contentDescriptor?.processHandler
        if (processHandler != null && !processHandler.isProcessTerminated && !processHandler.isProcessTerminating) {
            logger.info("Destroying process ${processHandler.pid()}.")
            processHandler.destroyProcess()
            logger.info("Process ${processHandler.pid()} has been destroyed.")
        }
    }

    return contentDescriptor ?: error("RunContentDescriptor is not available.")
}

private val logger = Logger.getInstance("me.fornever.avaloniarider.rider.RunConfigurationUtils")
