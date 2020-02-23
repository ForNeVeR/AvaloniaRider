package me.fornever.avaloniarider.previewer

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.jetbrains.rider.model.ProjectOutput
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import me.fornever.avaloniarider.exceptions.AvaloniaPreviewerInitializationException
import org.jetbrains.concurrency.await
import java.nio.file.Paths

@Service
class MsBuildParameterCollector(private val project: Project) {
    companion object {
        fun getInstance(project: Project): MsBuildParameterCollector =
            project.getService(MsBuildParameterCollector::class.java)

        private val logger = Logger.getInstance(MsBuildParameterCollector::class.java)
    }

    private fun getPathKey(runtime: DotNetRuntime): String = when (runtime) {
        is DotNetCoreRuntime -> "AvaloniaPreviewerNetCoreToolPath"
        else -> "AvaloniaPreviewerNetFullToolPath"
    }

    private fun createParameters(
        runtime: DotNetRuntime,
        runnableProject: RunnableProject,
        avaloniaPreviewerPathKey: String,
        properties: Map<String, String>): AvaloniaPreviewerParameters {
        fun getProperty(key: String, errorMessage: String? = null): String {
            val property = properties[key]
            if (property.isNullOrEmpty()) {
                // TODO[F]: Catch that in the session controller layer and show a notification (#41)
                throw AvaloniaPreviewerInitializationException(
                    errorMessage ?: "Cannot determine value of property \"$key\" from MSBuild")
            }

            return property
        }

        val previewerPath = Paths.get(
            getProperty(
                avaloniaPreviewerPathKey,
                "Avalonia could not be found. Please ensure project ${runnableProject.name} includes package Avalonia version 0.7 or higher"
            )
        )
        val targetDir = Paths.get(getProperty("TargetDir"))
        val targetName = getProperty("TargetName")
        val targetPath = Paths.get(getProperty("TargetPath"))

        return AvaloniaPreviewerParameters(runtime, previewerPath, targetDir, targetName, targetPath)
    }

    suspend fun getAvaloniaPreviewerParameters(
        runnableProject: RunnableProject,
        projectOutput: ProjectOutput): AvaloniaPreviewerParameters {
        val runtimeHost = RiderDotNetActiveRuntimeHost.getInstance(project)
        val msBuildEvaluator = MSBuildEvaluator.getInstance(project)

        val runtime = DotNetRuntime.detectRuntimeForProjectOrThrow(
            runnableProject.kind,
            runtimeHost,
            false,
            projectOutput.exePath,
            projectOutput.tfm
        )
        val avaloniaPreviewerPathKey = getPathKey(runtime)

        val properties = msBuildEvaluator.evaluateProperties(
            MSBuildEvaluator.PropertyRequest(
                runnableProject.projectFilePath,
                null,
                listOf(avaloniaPreviewerPathKey, "TargetDir", "TargetName", "TargetPath")
            )
        ).await()

        return createParameters(runtime, runnableProject, avaloniaPreviewerPathKey, properties)
    }
}
