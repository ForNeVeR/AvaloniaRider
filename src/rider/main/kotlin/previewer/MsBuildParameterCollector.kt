package me.fornever.avaloniarider.previewer

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.ide.model.RdProjectOutput
import com.jetbrains.rider.model.RdProjectDescriptor
import com.jetbrains.rider.model.RunnableProjectKind
import com.jetbrains.rider.projectView.nodes.ProjectModelNode
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
    }

    private fun getPathKey(runtime: DotNetRuntime): String = when (runtime) {
        is DotNetCoreRuntime -> "AvaloniaPreviewerNetCoreToolPath"
        else -> "AvaloniaPreviewerNetFullToolPath"
    }

    private fun createParameters(
        runtime: DotNetRuntime,
        runnableProject: ProjectModelNode,
        avaloniaPreviewerPathKey: String,
        properties: Map<String, String>): AvaloniaPreviewerParameters {
        fun getProperty(key: String, errorMessage: String? = null): String {
            val property = properties[key]
            if (property.isNullOrEmpty()) {
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
        project: ProjectModelNode,
        projectOutput: RdProjectOutput
    ): AvaloniaPreviewerParameters {
        val runtimeHost = RiderDotNetActiveRuntimeHost.getInstance(this.project)
        val msBuildEvaluator = MSBuildEvaluator.getInstance(this.project)

        val projectFilePath = project.getVirtualFile()!!.path
        val projectKind = if ((project.descriptor as RdProjectDescriptor).isDotNetCore)
            RunnableProjectKind.DotNetCore
        else
            RunnableProjectKind.Console

        val runtime = DotNetRuntime.detectRuntimeForProjectOrThrow(
            projectKind,
            runtimeHost,
            false,
            projectOutput.outputPath,
            projectOutput.tfm
        )
        val avaloniaPreviewerPathKey = getPathKey(runtime)

        val properties = msBuildEvaluator.evaluateProperties(
            MSBuildEvaluator.PropertyRequest(
                projectFilePath,
                null,
                listOf(avaloniaPreviewerPathKey, "TargetDir", "TargetName", "TargetPath")
            )
        ).await()

        return createParameters(runtime, project, avaloniaPreviewerPathKey, properties)
    }
}
