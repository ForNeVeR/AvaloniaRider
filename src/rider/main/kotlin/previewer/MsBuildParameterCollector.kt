package me.fornever.avaloniarider.previewer

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.rd.ide.model.RdProjectOutput
import com.jetbrains.rd.platform.util.getLogger
import com.jetbrains.rider.debugger.DebuggerHelperHost
import com.jetbrains.rider.model.RunnableProject
import com.jetbrains.rider.model.runnableProjectsModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.run.environment.MSBuildEvaluator
import com.jetbrains.rider.runtime.DotNetRuntime
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import com.jetbrains.rider.runtime.dotNetCore.DotNetCoreRuntime
import me.fornever.avaloniarider.exceptions.AvaloniaPreviewerInitializationException
import org.jetbrains.concurrency.await
import java.nio.file.Path
import java.nio.file.Paths

@Service
class MsBuildParameterCollector(private val project: Project) {
    companion object {
        fun getInstance(project: Project): MsBuildParameterCollector =
            project.getService(MsBuildParameterCollector::class.java)

        private val logger = getLogger<MsBuildParameterCollector>()
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
        project: Project,
        projectFilePath: Path,
        projectOutput: RdProjectOutput
    ): AvaloniaPreviewerParameters {
        val runtimeHost = RiderDotNetActiveRuntimeHost.getInstance(this.project)
        val msBuildEvaluator = MSBuildEvaluator.getInstance(this.project)

        val runnableProjects = project.solution.runnableProjectsModel.projects.valueOrNull
        val runnableProject =
            runnableProjects?.singleOrNull { FileUtil.pathsEqual(it.projectFilePath, projectFilePath.toString()) }
                ?: run {
                    logger.warn(
                        "Could not find runnable project for path $projectFilePath; all runnable projects are ${
                            runnableProjects?.joinToString { it.projectFilePath }
                        }"
                    )
                    throw AvaloniaPreviewerInitializationException(
                        "Could not find runnable project for path ${FileUtil.getNameWithoutExtension(projectFilePath.toString())}"
                    )
                }

        val runtime = DotNetRuntime.detectRuntimeForProjectOrThrow(
            runnableProject.kind,
            runtimeHost,
            DebuggerHelperHost.getInstance(this.project),
            false,
            projectOutput.outputPath,
            projectOutput.tfm
        )
        val avaloniaPreviewerPathKey = getPathKey(runtime)

        val properties = msBuildEvaluator.evaluateProperties(
            MSBuildEvaluator.PropertyRequest(
                projectFilePath.toString(),
                null,
                listOf(avaloniaPreviewerPathKey, "TargetDir", "TargetName", "TargetPath")
            )
        ).await()

        return createParameters(runtime, runnableProject, avaloniaPreviewerPathKey, properties)
    }
}
